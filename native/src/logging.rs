use std::{
    fs::{File, OpenOptions},
    io::{self, Write},
    path::PathBuf,
    sync::{Mutex, OnceLock},
};
use tracing::Level;
use tracing_subscriber::{
    Registry, filter::Targets, layer::SubscriberExt, reload, util::SubscriberInitExt,
};

const MAX_LOG: u64 = 1024 * 1024;
struct LogFile {
    path: PathBuf,
    file: File,
    size: u64,
}
static LOG: Mutex<Option<LogFile>> = Mutex::new(None);
static FILTER: OnceLock<reload::Handle<Targets, Registry>> = OnceLock::new();

fn filter(level: Level) -> Targets {
    Targets::new()
        .with_target("shadowquic", level)
        .with_target("quinn", std::cmp::min(Level::WARN, level))
}

struct Writer;
impl Write for Writer {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        let mut guard = LOG.lock().unwrap_or_else(|p| p.into_inner());
        if let Some(log) = guard.as_mut() {
            if log.size + bytes.len() as u64 > MAX_LOG {
                std::fs::rename(&log.path, log.path.with_extension("log.1"))?;
                log.file = OpenOptions::new()
                    .create(true)
                    .truncate(true)
                    .write(true)
                    .open(&log.path)?;
                log.size = 0;
            }
            log.file.write_all(bytes)?;
            log.size += bytes.len() as u64;
        }
        Ok(bytes.len())
    }
    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

pub fn setup(path: PathBuf, level: Level) -> Result<(), String> {
    let file = OpenOptions::new()
        .create(true)
        .append(true)
        .open(&path)
        .map_err(|e| e.to_string())?;
    let size = file.metadata().map_err(|e| e.to_string())?.len();
    *LOG.lock().unwrap_or_else(|p| p.into_inner()) = Some(LogFile { path, file, size });
    if let Some(handle) = FILTER.get() {
        handle.reload(filter(level)).map_err(|e| e.to_string())?;
    } else {
        let (filter, handle) = reload::Layer::new(filter(level));
        tracing_subscriber::registry()
            .with(filter)
            .with(
                tracing_subscriber::fmt::layer()
                    .with_ansi(false)
                    .with_writer(|| Writer),
            )
            .try_init()
            .map_err(|e| e.to_string())?;
        let _ = FILTER.set(handle);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn matches_shadowquic_target_filter_at_every_configured_level() {
        let levels = [
            Level::ERROR,
            Level::WARN,
            Level::INFO,
            Level::DEBUG,
            Level::TRACE,
        ];
        for configured in levels {
            let filter = filter(configured);
            for event in levels {
                assert_eq!(
                    filter.would_enable("shadowquic::android", &event),
                    event <= configured
                );
                assert_eq!(
                    filter.would_enable("quinn::connection", &event),
                    event <= std::cmp::min(Level::WARN, configured)
                );
                assert!(!filter.would_enable("hyper::client", &event));
                assert!(!filter.would_enable("rustls", &event));
            }
        }
    }

    #[test]
    fn rotates_and_caps_core_log() {
        let path = std::env::temp_dir().join(format!("squirrel-log-{}.log", std::process::id()));
        setup(path.clone(), Level::INFO).unwrap();
        let block = vec![b'x'; 65536];
        for _ in 0..20 {
            Writer.write_all(&block).unwrap();
        }
        assert!(path.metadata().unwrap().len() <= MAX_LOG);
        assert!(path.with_extension("log.1").exists());
        std::fs::remove_file(&path).unwrap();
        std::fs::remove_file(path.with_extension("log.1")).unwrap();
        *LOG.lock().unwrap() = None;
    }
}
