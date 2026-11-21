use std::{
    collections::BTreeSet,
    fs,
    path::{Component, Path, PathBuf},
};

// These files belong to the Android workspace, never the downloaded core data.
fn protected(path: &Path) -> bool {
    path.components().next().is_some_and(|part| {
        let name = part.as_os_str().to_string_lossy();
        matches!(name.as_ref(), "configs" | "logs")
            || name.starts_with("profiles.json")
            || name.starts_with("config.yaml")
    })
}

fn private_file(root: &Path, path: &Path) -> Option<PathBuf> {
    let absolute = if path.is_absolute() {
        path.to_owned()
    } else {
        root.join(path)
    };
    let mut normalized = PathBuf::new();
    for component in absolute.components() {
        match component {
            Component::ParentDir => {
                normalized.pop();
            }
            Component::CurDir => {}
            part => normalized.push(part.as_os_str()),
        }
    }
    let relative = normalized.strip_prefix(root).ok()?;
    if relative.as_os_str().is_empty() || protected(relative) {
        return None;
    }
    let mut current = root.to_owned();
    for part in relative.components() {
        current.push(part);
        // Never follow symlinks, even if a configured path points through one.
        if fs::symlink_metadata(&current).is_ok_and(|meta| meta.file_type().is_symlink()) {
            return None;
        }
    }
    Some(normalized)
}

fn collect(root: &Path, directory: &Path, targets: &mut BTreeSet<PathBuf>) -> Result<(), String> {
    for entry in fs::read_dir(directory).map_err(|_| "Cannot read app storage")? {
        let entry = entry.map_err(|_| "Cannot read app storage")?;
        let path = entry.path();
        if protected(
            path.strip_prefix(root)
                .map_err(|_| "Invalid storage path")?,
        ) {
            continue;
        }
        let kind = entry
            .file_type()
            .map_err(|_| "Cannot inspect app storage")?;
        if kind.is_dir() {
            collect(root, &path, targets)?;
        } else if kind.is_file() {
            let extension = path
                .extension()
                .unwrap_or_default()
                .to_string_lossy()
                .to_ascii_lowercase();
            if matches!(
                extension.as_str(),
                "mmdb" | "redb" | "db" | "sqlite" | "sqlite3" | "html" | "htm"
            ) {
                targets.insert(path);
            }
        }
    }
    Ok(())
}

/// Called while holding the native session lock, with the core stopped.
pub fn clear(root: &Path, configurations: &[String]) -> Result<usize, String> {
    let requested_root = root;
    let root = root.canonicalize().map_err(|_| "Cannot open app storage")?;
    let mut targets = BTreeSet::new();
    // Read every configuration before removing anything, including custom file names.
    for configuration in configurations {
        let document = crate::config::document(configuration)
            .map_err(|_| "Cannot read database paths from a saved configuration")?;
        if let Some(databases) = document
            .pointer("/router/database")
            .and_then(|value| value.as_array())
        {
            for database in databases {
                if let Some(path) = database.get("path").and_then(|value| value.as_str()) {
                    let original = Path::new(path);
                    let resolved = original
                        .strip_prefix(requested_root)
                        .map(|relative| root.join(relative));
                    let path = resolved.as_deref().unwrap_or(original);
                    if let Some(path) = private_file(&root, path) {
                        targets.insert(path);
                    }
                }
            }
        }
    }
    // Include files left by older or deleted profiles, plus downloaded dashboard HTML.
    collect(&root, &root, &mut targets)?;
    let mut removed = 0;
    for path in targets {
        match fs::remove_file(&path) {
            Ok(()) => removed += 1,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => return Err("Could not remove a database or HTML file. Some files may already be cleared. Try again.".into()),
        }
    }
    Ok(removed)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};

    static NEXT: AtomicUsize = AtomicUsize::new(0);
    struct Fixture(PathBuf);
    impl Fixture {
        fn new() -> Self {
            let path = std::env::temp_dir().join(format!(
                "squirrel-storage-{}-{}",
                std::process::id(),
                NEXT.fetch_add(1, Ordering::Relaxed)
            ));
            fs::create_dir_all(&path).unwrap();
            Self(path)
        }
        fn file(&self, name: &str) -> PathBuf {
            let path = self.0.join(name);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(&path, "synthetic fixture").unwrap();
            path
        }
    }
    impl Drop for Fixture {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.0);
        }
    }

    #[test]
    fn clears_all_profiles_custom_paths_orphans_and_html_but_keeps_workspace() {
        let fixture = Fixture::new();
        let removed = [
            "data/country.mmdb",
            "old/site.redb",
            "dashboard/index.html",
            "custom/rules.binary",
            "custom/other.cache",
            "orphan/old.DB",
        ];
        let kept = [
            "configs/saved.yaml",
            "configs/backup.db",
            "logs/core.log",
            "profiles.json",
            "config.yaml",
            "data/client.pem",
            "notes.txt",
        ];
        for name in removed.iter().chain(kept.iter()) {
            fixture.file(name);
        }
        let configs = [
            "router: {database: [{path: custom/rules.binary}]}".into(),
            "router: {database: [{path: custom/other.cache}]}".into(),
        ];
        assert_eq!(clear(&fixture.0, &configs).unwrap(), removed.len());
        for name in removed {
            assert!(!fixture.0.join(name).exists(), "{name}");
        }
        for name in kept {
            assert!(fixture.0.join(name).exists(), "{name}");
        }
        assert_eq!(clear(&fixture.0, &configs).unwrap(), 0);
    }

    #[test]
    fn rejects_invalid_configuration_before_deleting_anything() {
        let fixture = Fixture::new();
        let database = fixture.file("data/site.redb");
        assert!(clear(&fixture.0, &["[invalid".into()]).is_err());
        assert!(database.exists());
    }

    #[cfg(unix)]
    #[test]
    fn resolves_configured_absolute_paths_through_the_private_root_alias() {
        let fixture = Fixture::new();
        let aliases = Fixture::new();
        let alias = aliases.0.join("files");
        std::os::unix::fs::symlink(&fixture.0, &alias).unwrap();
        let database = fixture.file("custom.binary");
        let config = format!(
            "router: {{database: [{{path: {}}}]}}",
            alias.join("custom.binary").display()
        );
        assert_eq!(clear(&alias, &[config]).unwrap(), 1);
        assert!(!database.exists());
    }

    #[cfg(unix)]
    #[test]
    fn keeps_external_files_symlink_targets_and_reserved_paths() {
        let fixture = Fixture::new();
        let outside = Fixture::new();
        let external = outside.file("outside.mmdb");
        std::os::unix::fs::symlink(&outside.0, fixture.0.join("link")).unwrap();
        std::os::unix::fs::symlink(&external, fixture.0.join("linked.redb")).unwrap();
        let saved = fixture.file("configs/saved.yaml");
        let index = fixture.file("profiles.json");
        let config = format!(
            "router:\n  database:\n    - path: {}\n    - path: link/outside.mmdb\n    - path: linked.redb\n    - path: configs/saved.yaml\n    - path: data/../profiles.json\n",
            external.display()
        );
        assert_eq!(clear(&fixture.0, &[config]).unwrap(), 0);
        assert!(external.exists());
        assert!(saved.exists());
        assert!(index.exists());
    }
}
