use std::{
    fs::{File, OpenOptions},
    io::{self, Write},
    path::PathBuf,
    sync::{Mutex, OnceLock},
};
use tracing_subscriber::{
    EnvFilter, Registry, layer::SubscriberExt, reload, util::SubscriberInitExt,
};

const MAX_LOG: u64 = 1024 * 1024;
struct LogFile {
    path: PathBuf,
    file: File,
    size: u64,
}
static LOG: Mutex<Option<LogFile>> = Mutex::new(None);
static FILTER: OnceLock<reload::Handle<EnvFilter, Registry>> = OnceLock::new();

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

pub fn setup(path: PathBuf, level: &str) -> Result<(), String> {
    let file = OpenOptions::new()
        .create(true)
        .append(true)
        .open(&path)
        .map_err(|e| e.to_string())?;
    let size = file.metadata().map_err(|e| e.to_string())?.len();
    *LOG.lock().unwrap_or_else(|p| p.into_inner()) = Some(LogFile { path, file, size });
    if let Some(handle) = FILTER.get() {
        handle
            .reload(EnvFilter::new(level))
            .map_err(|e| e.to_string())?;
    } else {
        let (filter, handle) = reload::Layer::new(EnvFilter::new(level));
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
    fn rotates_and_caps_core_log() {
        let path = std::env::temp_dir().join(format!("squirrel-log-{}.log", std::process::id()));
        setup(path.clone(), "info").unwrap();
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
