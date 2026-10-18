//! Standalone remote control-plane client. Never touches the VPN runtime or its descriptors.
use serde_json::{Value, json};
use shadowquic::{
    Inbound, Outbound,
    config::{AuthUser, Config, DnsCfg, OutboundCfg},
    dns::{DnsService, ResolverManager},
    msgs::squic::{SQExtError, UserStats},
    shadowquic::outbound::ShadowQuicClient,
    squic::inbound::UserManager,
    sunnyquic::outbound::SunnyQuicClient,
    utils::socket_opt::UdpSocketFactory,
};
use std::{sync::Arc, time::Duration};

fn parse(text: &str) -> Result<Config, String> {
    // Parse errors may embed YAML scalar values; never return credentials to the UI/logs.
    crate::config::parse(text)
        .map(|(cfg, _)| cfg)
        .map_err(|_| "Cannot read saved configuration. Validate it in Configuration first.".into())
}

pub fn outbounds(text: &str) -> Result<Value, String> {
    let cfg = parse(text)?;
    let mut values = Vec::new();
    for outbound in cfg.outbounds {
        let (tag, protocol, username) = match outbound {
            OutboundCfg::ShadowQuic(c) => (c.tag, "shadowquic", c.username),
            OutboundCfg::SunnyQuic(c) => (c.tag, "sunnyquic", c.username),
            _ => continue,
        };
        values.push(json!({"tag":tag, "protocol":protocol, "username":username,
            "admin":username.starts_with("admin")}));
    }
    values.sort_by(|a, b| a["tag"].as_str().cmp(&b["tag"].as_str()));
    Ok(json!({"outbounds":values}))
}

pub fn request(
    text: &str,
    tag: &str,
    operation: &str,
    username: &str,
    password: &str,
) -> Result<Value, String> {
    validate_request(operation, username, password)?;
    let cfg = parse(text)?;
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .build()
        .map_err(|_| "Cannot start API worker".to_owned())?;
    let result = runtime.block_on(async {
        tokio::time::timeout(Duration::from_secs(20), execute(cfg, tag, operation, username, password))
            .await.map_err(|_| "Request timed out. The server may have applied a change; check its state before retrying.".to_owned())?
    });
    runtime.shutdown_timeout(Duration::from_secs(1));
    result
}

fn validate_request(operation: &str, username: &str, password: &str) -> Result<(), String> {
    if !matches!(
        operation,
        "list-users" | "add-user" | "remove-user" | "kill-conn" | "get-stats" | "clear-stats"
    ) {
        return Err("Unknown server API operation".into());
    }
    if matches!(operation, "add-user" | "remove-user" | "kill-conn") && username.is_empty() {
        return Err("Enter a username".into());
    }
    if username.len() > 4096 || username.chars().any(char::is_control) {
        return Err("Use a single-line username of at most 4096 bytes".into());
    }
    if operation == "add-user" && (password.is_empty() || password.len() > 4096) {
        return Err("Enter a password of 1–4096 bytes".into());
    }
    Ok(())
}

async fn execute(
    mut cfg: Config,
    tag: &str,
    operation: &str,
    username: &str,
    password: &str,
) -> Result<Value, String> {
    let outbound = cfg
        .outbounds
        .into_iter()
        .find(|c| c.tag() == tag)
        .ok_or("Outbound no longer exists in this saved profile")?;
    let (login, resolver_tag) = match &outbound {
        OutboundCfg::ShadowQuic(c) => (&c.username, c.addr_resolver.as_deref()),
        OutboundCfg::SunnyQuic(c) => (&c.username, c.addr_resolver.as_deref()),
        _ => return Err("Choose a ShadowQUIC or SunnyQUIC outbound".into()),
    };
    if !login.starts_with("admin") {
        return Err("PermissionDenied: the outbound username must start with admin.".into());
    }
    let mut dns_tasks = tokio::task::JoinSet::new();
    let resolver: Option<Arc<dyn DnsService>> = if let Some(tag) =
        resolver_tag.filter(|tag| *tag != "default-system")
    {
        let mut dns = cfg
            .dns
            .drain(..)
            .find(|c| c.tag() == tag)
            .ok_or("Unknown address resolver")?;
        // Use the configured backend without binding its production listener or routing DNS into the VPN.
        let bind = "127.0.0.1:0".parse().unwrap();
        match &mut dns {
            DnsCfg::Udp(c) => c.bind_addr = bind,
            DnsCfg::Tcp(c) => c.bind_addr = bind,
            DnsCfg::Tls(c) => c.bind_addr = bind,
            DnsCfg::System(c) => c.bind_addr = bind,
            DnsCfg::FakeIp(_) => {
                return Err("A fake-IP resolver cannot resolve the remote API server".into());
            }
        }
        let mut server = dns
            .build()
            .await
            .map_err(|_| "Cannot start the configured address resolver")?;
        let resolver = server.resolver.clone();
        dns_tasks.spawn(async move {
            let direct = shadowquic::direct::outbound::DirectOut::default();
            let mut requests = tokio::task::JoinSet::new();
            loop {
                tokio::select! {
                    request = server.accept() => match request {
                        Ok(request) => { let direct = direct.clone(); requests.spawn(async move { let _ = direct.handle(request).await; }); }
                        Err(_) => break,
                    },
                    _ = requests.join_next(), if !requests.is_empty() => {}
                }
            }
        });
        Some(resolver)
    } else {
        None
    };
    // Both clients use the same public socket factory/resolver contract. Defaults and TLS settings remain upstream's.
    macro_rules! client {
        ($kind:ty, $config:expr) => {{
            let mut c = $config;
            c.addr_resolver = None;
            let mut client = <$kind>::new(c, Arc::new(ResolverManager::new()));
            if let Some(resolver) = resolver {
                client.socket_factory = Arc::new(UdpSocketFactory {
                    addr: client.config.addr.clone(),
                    interface: client.config.socket_opt.bind_interface.clone(),
                    fw_mark: client.config.socket_opt.fw_mark,
                    protect_path: client.config.protect_path.clone(),
                    try_dual_stack: true,
                    resolver: resolver.clone(),
                });
                client.addr_resolver = resolver;
            }
            dispatch(&client, operation, username, password).await
        }};
    }
    match outbound {
        OutboundCfg::ShadowQuic(c) => client!(ShadowQuicClient, c),
        OutboundCfg::SunnyQuic(c) => client!(SunnyQuicClient, c),
        _ => unreachable!(),
    }
}

fn api_error(error: SQExtError) -> String {
    match error {
        SQExtError::PermissionDenied => "PermissionDenied: this account cannot administer the server.",
        SQExtError::NotFound => "NotFound: the user does not exist on this server.",
        SQExtError::NotAvailable => "NotAvailable: this server does not support the operation. Check its version.",
        SQExtError::Other(_) => "Remote API failed. Check the server address, credentials, TLS settings and connectivity. A change may already have been applied; check before retrying.",
    }.into()
}

async fn dispatch(
    client: &dyn UserManager,
    operation: &str,
    username: &str,
    password: &str,
) -> Result<Value, String> {
    let result = match operation {
        "list-users" => {
            let mut users = client.list_users().await.map_err(api_error)?;
            users.sort();
            return Ok(json!({"users":users}));
        }
        "get-stats" => {
            let mut stats = if username.is_empty() {
                client.get_all_stats().await.map_err(api_error)?
            } else {
                vec![client.get_user_stats(username).await.map_err(api_error)?]
            };
            stats.sort_by(|a, b| a.username.cmp(&b.username));
            return Ok(
                json!({"stats":stats.iter().map(|s| stats_json(s, username)).collect::<Vec<_>>()}),
            );
        }
        "add-user" => {
            client
                .add_user(AuthUser {
                    username: username.into(),
                    password: password.into(),
                })
                .await
        }
        "remove-user" => client.remove_user(username).await,
        "kill-conn" => client.kill_user_conns(username).await,
        "clear-stats" if username.is_empty() => client.clear_all_stats().await,
        "clear-stats" => client.clear_user_stats(username).await,
        _ => return Err("Unknown server API operation".into()),
    };
    result.map_err(api_error)?;
    Ok(json!({"operation":operation, "username":username, "ok":true}))
}

fn stats_json(s: &UserStats, fallback: &str) -> Value {
    json!({"username":if s.username.is_empty() {fallback} else {&s.username}, "conn_num":s.conn_num,
        "tcp_conns":s.tcp_conns, "udp_conns":s.udp_conns, "tcp_sent":s.tcp_sent, "tcp_recv":s.tcp_recv,
        "udp_sent":s.udp_sent, "udp_recv":s.udp_recv})
}

#[cfg(test)]
mod tests;
