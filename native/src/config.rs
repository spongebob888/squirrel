use serde_json::{Value, json};
use shadowquic::config::{Config, InboundCfg};
use std::path::{Component, Path, PathBuf};

fn document(text: &str) -> Result<Value, String> {
    if text.len() > 1024 * 1024 {
        return Err("Configuration exceeds 1 MiB".into());
    }
    let document: Value = serde_saphyr::from_str(text).map_err(|e| e.to_string())?;
    if !document.is_object() {
        return Err("Configuration must be a YAML mapping".into());
    }
    Ok(document)
}

pub fn normalize_database_paths(text: &str, private_root: &Path) -> Result<String, String> {
    let mut document = document(text)?;
    let Some(databases) = document.pointer_mut("/router/database") else {
        return Ok(text.to_owned());
    };
    let databases = databases
        .as_array_mut()
        .ok_or("router.database must be a list")?;
    if !private_root.is_absolute() {
        return Err("Database root must be an absolute app-private directory".into());
    }
    for database in databases {
        let path = database
            .get("path")
            .and_then(Value::as_str)
            .filter(|path| !path.is_empty())
            .ok_or("Router database path must be a nonempty string")?;
        if Path::new(path).is_absolute() {
            continue;
        }
        let mut relative = PathBuf::new();
        for component in Path::new(path).components() {
            match component {
                Component::Normal(part) => relative.push(part),
                Component::CurDir => {}
                Component::ParentDir if relative.pop() => {}
                _ => return Err("Relative database path escapes the app-private directory".into()),
            }
        }
        if relative.as_os_str().is_empty() {
            return Err("Router database path must name a file".into());
        }
        database["path"] = json!(private_root.join(relative));
    }
    // JSON is valid YAML. Only the runtime copy is serialized; the editor and
    // saved/imported YAML retain comments and portable relative paths.
    serde_json::to_string(&document).map_err(|error| error.to_string())
}

pub fn prepare_android_config(
    text: &str,
    private_root: &Path,
    api_secret: &str,
) -> Result<String, String> {
    if api_secret.is_empty() {
        return Err("Android API secret must not be empty".into());
    }
    let normalized = normalize_database_paths(text, private_root)?;
    let mut document = document(&normalized)?;
    document["restful-api"] = json!({
        "bind-addr": "127.0.0.1:19090",
        "secret": api_secret,
    });
    serde_json::to_string(&document).map_err(|error| error.to_string())
}

pub fn parse(text: &str) -> Result<(Config, Value), String> {
    let mut document = document(text)?;
    let root = document
        .as_object_mut()
        .ok_or("Configuration must be a YAML mapping")?;
    let android = root.remove("android").unwrap_or_else(|| json!({}));
    let settings = android.as_object().ok_or("android must be a mapping")?;
    if settings.keys().any(|k| k != "dns-servers") {
        return Err("Unknown android setting; supported: dns-servers".into());
    }
    let dns: Vec<String> = match settings.get("dns-servers") {
        Some(value) => serde_json::from_value(value.clone())
            .map_err(|_| "dns-servers must be a list of IP addresses")?,
        None => vec!["1.1.1.1".into(), "1.0.0.1".into()],
    };
    if dns.is_empty()
        || dns.len() > 8
        || dns.iter().any(|s| {
            s.parse::<std::net::IpAddr>().map_or(true, |ip| {
                ip.is_unspecified() || ip.is_multicast() || ip.is_loopback()
            })
        })
    {
        return Err("Specify 1–8 unicast DNS IP addresses (not loopback)".into());
    }
    if root.contains_key("inbound") && root.contains_key("inbounds") {
        return Err("Use either inbound or inbounds, not both".into());
    }
    if let Some(mut legacy) = root.remove("inbound") {
        if let Some(map) = legacy.as_object_mut() {
            map.entry("tag").or_insert(json!("inbound"));
            root.insert("inbounds".into(), json!([legacy]));
        } else {
            root.insert("inbounds".into(), legacy);
        }
    }
    let inbounds = root
        .entry("inbounds")
        .or_insert(json!([]))
        .as_array_mut()
        .ok_or("inbounds must be a list")?;
    if !inbounds
        .iter()
        .any(|v| v.get("type").and_then(Value::as_str) == Some("tun"))
    {
        inbounds.push(json!({"type": "tun", "tag": "squirrel-tun"}));
    }
    let mut cfg: Config = serde_json::from_value(document).map_err(|e| e.to_string())?;
    cfg.validate().map_err(|e| e.to_string())?;
    let tuns: Vec<_> = cfg
        .inbounds
        .iter()
        .filter_map(|i| match i {
            InboundCfg::Tun(tun) => Some(tun),
            _ => None,
        })
        .collect();
    if tuns.len() != 1 {
        return Err("Android supports exactly one TUN inbound".into());
    }
    let tun = tuns[0];
    let vpn = json!({
        "ipv4": tun.ipv4.addr().to_string(), "ipv4Prefix": tun.ipv4.prefix_len(),
        "ipv6": tun.ipv6.addr().to_string(), "ipv6Prefix": tun.ipv6.prefix_len(),
        "mtu": tun.mtu, "dns": dns,
    });
    // Package bypass protects every socket owned by this UID. Imported desktop
    // protection socket paths are unnecessary and may not exist on this device.
    for outbound in &mut cfg.outbounds {
        match outbound {
            shadowquic::config::OutboundCfg::ShadowQuic(c) => c.protect_path = None,
            shadowquic::config::OutboundCfg::SunnyQuic(c) => c.protect_path = None,
            _ => {}
        }
    }
    Ok((cfg, vpn))
}

#[cfg(test)]
mod tests {
    use super::*;
    const DIRECT: &str = "outbounds: [{type: direct, tag: direct}]";

    #[test]
    fn roots_country_and_geosite_paths_without_changing_other_settings() {
        let text = format!(
            "{DIRECT}\nandroid: {{dns-servers: [9.9.9.9]}}\nrouter:\n  database:\n    - {{type: country, tag: country, path: country.mmdb}}\n    - {{type: geosite, tag: site, path: ./data/old/../geosite.redb, list-include: [cn]}}\n"
        );
        let normalized = normalize_database_paths(&text, Path::new("/private/files")).unwrap();
        let (cfg, vpn) = parse(&normalized).unwrap();
        assert_eq!(
            cfg.router.database[0].path(),
            Path::new("/private/files/country.mmdb")
        );
        assert_eq!(
            cfg.router.database[1].path(),
            Path::new("/private/files/data/geosite.redb")
        );
        assert_eq!(vpn["dns"][0], "9.9.9.9");
        let document = document(&normalized).unwrap();
        assert_eq!(
            document["router"]["database"][1]["list-include"],
            json!(["cn"])
        );
    }

    #[test]
    fn keeps_absolute_paths_and_configs_without_databases() {
        assert_eq!(
            normalize_database_paths(DIRECT, Path::new("/private/files")).unwrap(),
            DIRECT
        );
        let text = format!(
            "{DIRECT}\nrouter: {{database: [{{type: country, tag: db, path: /existing/country.mmdb}}]}}"
        );
        let normalized = normalize_database_paths(&text, Path::new("/private/files")).unwrap();
        assert_eq!(
            parse(&normalized).unwrap().0.router.database[0].path(),
            Path::new("/existing/country.mmdb")
        );
    }

    #[test]
    fn rejects_database_paths_that_escape_or_do_not_name_files() {
        for path in [
            "../country.mmdb",
            "data/../../country.mmdb",
            "",
            ".",
            "data/..",
        ] {
            let text = format!(
                "{DIRECT}\nrouter: {{database: [{{type: country, tag: db, path: '{path}'}}]}}"
            );
            assert!(
                normalize_database_paths(&text, Path::new("/private/files")).is_err(),
                "{path}"
            );
        }
    }

    #[test]
    fn detects_duplicate_database_paths_after_normalization() {
        let text = format!(
            "{DIRECT}\nrouter:\n  database:\n    - {{type: country, tag: first, path: data/../country.mmdb}}\n    - {{type: country, tag: second, path: country.mmdb}}\n"
        );
        let normalized = normalize_database_paths(&text, Path::new("/private/files")).unwrap();
        assert!(parse(&normalized).unwrap_err().contains("unique"));
    }

    #[test]
    fn injects_tun_and_returns_android_settings() {
        let (cfg, vpn) = parse(&format!("{DIRECT}\nandroid:\n  dns-servers: [9.9.9.9]\n")).unwrap();
        assert_eq!(cfg.inbounds.len(), 1);
        assert_eq!(vpn["dns"][0], "9.9.9.9");
        assert_eq!(vpn["mtu"], 1500);
    }

    #[test]
    fn preserves_tun_addresses_and_routing() {
        let (cfg, vpn) = parse(&format!("{DIRECT}\ninbounds:\n  - type: tun\n    tag: phone\n    ipv4: 10.7.0.2/24\n    mtu: 1400\n    default-outbound: direct\n")).unwrap();
        assert_eq!(vpn["ipv4"], "10.7.0.2");
        assert_eq!(vpn["mtu"], 1400);
        assert_eq!(cfg.inbounds[0].default_outbound(), Some("direct"));
    }

    #[test]
    fn accepts_upstream_legacy_client_config() {
        let (cfg, _) = parse(
            "inbound: {type: socks, bind-addr: '127.0.0.1:1089'}\noutbound: {type: direct}\n",
        )
        .unwrap();
        assert_eq!(cfg.inbounds[0].tag(), "inbound");
        assert_eq!(cfg.inbounds.len(), 2);
    }

    #[test]
    fn rejects_invalid_dns_multiple_tuns_and_unknown_routes() {
        for extra in [
            "android: {dns-servers: []}",
            "android: {dns-servers: [localhost]}",
            "android: {dns-servers: [127.0.0.1]}",
            "android: {typo: true}",
            "inbounds: [{type: tun, tag: a}, {type: tun, tag: b}]",
            "router: {default-outbound: missing}",
        ] {
            assert!(parse(&format!("{DIRECT}\n{extra}\n")).is_err(), "{extra}");
        }
    }

    #[test]
    fn bundled_examples_validate() {
        parse(include_str!("../../examples/shadowquic.yaml")).unwrap();
        parse(include_str!("../../examples/direct.yaml")).unwrap();
    }
}
