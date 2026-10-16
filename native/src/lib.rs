mod config;
#[cfg(any(target_os = "android", test))]
mod logging;

use jni::{
    JNIEnv,
    objects::{JClass, JString},
    sys::{jint, jstring},
};
use std::{
    panic::{AssertUnwindSafe, catch_unwind},
    sync::Mutex,
    time::Duration,
};
use tokio::{runtime::Runtime, sync::oneshot, task::JoinHandle};

struct Session {
    runtime: Runtime,
    stop: oneshot::Sender<()>,
    task: JoinHandle<()>,
}
static SESSION: Mutex<Option<Session>> = Mutex::new(None);

#[cfg(target_os = "android")]
fn start(fd: i32, text: &str, log_path: &str) -> Result<(), String> {
    let mut slot = SESSION.lock().unwrap_or_else(|p| p.into_inner());
    if slot.is_some() {
        return Err("VPN core is already running".into());
    }
    let (mut cfg, _) = config::parse(text)?;
    {
        use std::{os::fd::BorrowedFd, sync::Arc};
        if fd < 0 {
            return Err("Invalid VPN descriptor".into());
        }
        // Java retains its ParcelFileDescriptor; all Rust copies have RAII ownership.
        let owned = unsafe { BorrowedFd::borrow_raw(fd) }
            .try_clone_to_owned()
            .map_err(|e| e.to_string())?;
        for inbound in &mut cfg.inbounds {
            if let shadowquic::config::InboundCfg::Tun(tun) = inbound {
                tun.android_fd = Some(Arc::new(owned));
                break;
            }
        }
    }
    {
        logging::setup(
            log_path.into(),
            &format!("{:?}", cfg.log_level).to_lowercase(),
        )?;
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .enable_all()
            .build()
            .map_err(|e| e.to_string())?;
        let manager = runtime.block_on(async {
            tokio::time::timeout(Duration::from_secs(20), cfg.build_manager())
                .await
                .map_err(|_| "Core startup timed out".to_owned())?
                .map_err(|e| e.to_string())
        })?;
        let (stop, stopped) = oneshot::channel();
        let task = runtime.spawn(async move {
            tracing::info!("ShadowQUIC VPN core started; application UID bypass is active");
            if let Err(error) = manager
                .run_until(async {
                    let _ = stopped.await;
                })
                .await
            {
                tracing::error!(%error, "VPN core failed");
            }
            tracing::info!("VPN core stopped");
        });
        *slot = Some(Session {
            runtime,
            stop,
            task,
        });
        Ok(())
    }
}

#[cfg(not(target_os = "android"))]
fn start(_: i32, text: &str, _: &str) -> Result<(), String> {
    config::parse(text)?;
    Err("Starting the VPN requires Android".into())
}

fn stop() -> Result<(), String> {
    let session = SESSION.lock().unwrap_or_else(|p| p.into_inner()).take();
    if let Some(Session {
        runtime,
        stop,
        mut task,
    }) = session
    {
        let _ = stop.send(());
        runtime.block_on(async {
            if tokio::time::timeout(Duration::from_secs(3), &mut task)
                .await
                .is_err()
            {
                task.abort();
                let _ = task.await;
            }
        });
        runtime.shutdown_timeout(Duration::from_secs(1));
    }
    Ok(())
}

fn guarded<T>(
    env: &mut JNIEnv,
    action: impl FnOnce(&mut JNIEnv) -> Result<T, String>,
) -> Option<T> {
    match catch_unwind(AssertUnwindSafe(|| action(env))) {
        Ok(Ok(value)) => Some(value),
        outcome => {
            let message = match outcome {
                Ok(Err(error)) => error,
                _ => "Unexpected native core failure".into(),
            };
            let _ = env.throw_new("java/io/IOException", message);
            None
        }
    }
}

fn string(env: &mut JNIEnv, value: JString) -> Result<String, String> {
    env.get_string(&value)
        .map(Into::into)
        .map_err(|e| e.to_string())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_squirrel_vpn_NativeBridge_normalizeDatabasePaths(
    mut env: JNIEnv,
    _: JClass,
    text: JString,
    private_root: JString,
) -> jstring {
    guarded(&mut env, |env| {
        let text = string(env, text)?;
        let private_root = string(env, private_root)?;
        let normalized =
            config::normalize_database_paths(&text, std::path::Path::new(&private_root))?;
        env.new_string(normalized)
            .map(|s| s.into_raw())
            .map_err(|e| e.to_string())
    })
    .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_squirrel_vpn_NativeBridge_validate(
    mut env: JNIEnv,
    _: JClass,
    text: JString,
) -> jstring {
    guarded(&mut env, |env| {
        let (_, vpn) = config::parse(&string(env, text)?)?;
        env.new_string(vpn.to_string())
            .map(|s| s.into_raw())
            .map_err(|e| e.to_string())
    })
    .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_squirrel_vpn_NativeBridge_start(
    mut env: JNIEnv,
    _: JClass,
    fd: jint,
    text: JString,
    path: JString,
) {
    guarded(&mut env, |env| {
        start(fd, &string(env, text)?, &string(env, path)?)
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_squirrel_vpn_NativeBridge_stop(mut env: JNIEnv, _: JClass) {
    guarded(&mut env, |_| stop());
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_squirrel_vpn_NativeBridge_status(
    mut env: JNIEnv,
    _: JClass,
) -> jstring {
    guarded(&mut env, |env| {
        let guard = SESSION.lock().unwrap_or_else(|p| p.into_inner());
        let status = match guard.as_ref() {
            Some(session) if !session.task.is_finished() => "",
            _ => "ShadowQUIC core stopped. See the core log for details.",
        };
        env.new_string(status)
            .map(|s| s.into_raw())
            .map_err(|e| e.to_string())
    })
    .unwrap_or(std::ptr::null_mut())
}
