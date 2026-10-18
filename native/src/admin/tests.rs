use super::*;

fn profile(outbound: Value) -> String {
    json!({"outbounds":[outbound]}).to_string()
}

fn shadow(addr: &str, username: &str) -> Value {
    json!({"tag":"server", "type":"shadowquic", "addr":addr, "username":username,
        "password":"synthetic-test-password", "server-name":"localhost", "alpn":["h3"],
        "gso":false, "zero-rtt":false})
}

#[test]
fn discovery_exposes_only_supported_targets_without_credentials() {
    let text = json!({"outbounds":[shadow("127.0.0.1:9", "admin"),
        {"tag":"direct", "type":"direct"},
        {"tag":"sunny", "type":"sunnyquic", "addr":"127.0.0.1:9", "username":"bob",
            "password":"synthetic-test-password", "server-name":"localhost"}]})
    .to_string();
    let found = outbounds(&text).unwrap();
    assert_eq!(found["outbounds"].as_array().unwrap().len(), 2);
    assert_eq!(found["outbounds"][0]["admin"], true);
    assert_eq!(found["outbounds"][1]["admin"], false);
    assert!(!found.to_string().contains("synthetic-test-password"));
    assert!(
        !outbounds("password: synthetic-test-password")
            .unwrap_err()
            .contains("synthetic-test-password")
    );
}

#[test]
fn rejects_invalid_operations_and_non_admin_targets_before_connecting() {
    for op in ["add-user", "remove-user", "kill-conn"] {
        assert!(validate_request(op, "", "test").is_err());
    }
    assert!(validate_request("clear-stats", "", "").is_ok());
    assert!(validate_request("get-stats", "", "").is_ok());
    assert!(validate_request("get-stats", "a\nb", "").is_err());
    assert!(
        request(
            &profile(shadow("127.0.0.1:9", "bob")),
            "server",
            "list-users",
            "",
            ""
        )
        .unwrap_err()
        .contains("PermissionDenied")
    );
    assert!(
        request(
            &profile(json!({"tag":"direct", "type":"direct"})),
            "direct",
            "list-users",
            "",
            ""
        )
        .unwrap_err()
        .contains("ShadowQUIC or SunnyQUIC")
    );
}

#[test]
fn remote_commands_work_against_loopback_shadowquic_without_starting_vpn() {
    use shadowquic::{
        config::{AuthUser, JlsUpstream, ShadowQuicServerCfg},
        shadowquic::inbound::ShadowQuicServer,
    };
    let port = std::net::UdpSocket::bind("127.0.0.1:0")
        .unwrap()
        .local_addr()
        .unwrap()
        .port();
    let addr = format!("127.0.0.1:{port}");
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .worker_threads(2)
        .build()
        .unwrap();
    runtime.block_on(async {
        let server = ShadowQuicServer::new(ShadowQuicServerCfg {
            tag: "server".into(),
            bind_addr: addr.parse().unwrap(),
            users: vec![AuthUser {
                username: "admin".into(),
                password: "synthetic-test-password".into(),
            }],
            jls_upstream: JlsUpstream {
                addr: "localhost:443".into(),
                ..Default::default()
            },
            alpn: vec!["h3".into()],
            gso: false,
            zero_rtt: false,
            ..Default::default()
        })
        .await
        .unwrap();
        server.init().await.unwrap();
        let text = profile(shadow(&addr, "admin"));
        let call = |operation: &'static str, user: &'static str, password: &'static str| {
            let cfg = parse(&text).unwrap();
            async move {
                tokio::time::timeout(
                    Duration::from_secs(5),
                    execute(cfg, "server", operation, user, password),
                )
                .await
                .unwrap()
            }
        };
        assert_eq!(
            call("list-users", "", "").await.unwrap()["users"],
            json!(["admin"])
        );
        // Resolve the admin host through its configured backend, without binding the profile's listener.
        let dns = tokio::net::UdpSocket::bind("127.0.0.1:0").await.unwrap();
        let dns_addr = dns.local_addr().unwrap();
        let dns_task = tokio::spawn(async move {
            let mut packet = [0u8; 2048];
            while let Ok((len, peer)) = dns.recv_from(&mut packet).await {
                let mut end = 12;
                while end < len && packet[end] != 0 {
                    end += packet[end] as usize + 1;
                }
                end += 5;
                if end > len {
                    continue;
                }
                let ipv4 = packet[end - 4..end - 2] == [0, 1];
                let mut reply = packet[..2].to_vec();
                reply.extend_from_slice(&[0x81, 0x80, 0, 1, 0, u8::from(ipv4), 0, 0, 0, 0]);
                reply.extend_from_slice(&packet[12..end]);
                if ipv4 {
                    reply.extend_from_slice(&[
                        0xc0, 0x0c, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 127, 0, 0, 1,
                    ]);
                }
                let _ = dns.send_to(&reply, peer).await;
            }
        });
        let mut custom = shadow(&format!("admin.test:{port}"), "admin");
        custom["addr-resolver"] = json!("bootstrap");
        let custom = json!({"outbounds":[custom], "dns":[{"tag":"bootstrap", "type":"dns-udp",
            "bind-addr":"192.0.2.1:53", "upstream":dns_addr}]})
        .to_string();
        assert_eq!(
            tokio::time::timeout(
                Duration::from_secs(5),
                execute(parse(&custom).unwrap(), "server", "list-users", "", "")
            )
            .await
            .unwrap()
            .unwrap()["users"],
            json!(["admin"])
        );
        dns_task.abort();
        let _ = dns_task.await;
        call("add-user", "alice", "synthetic-user-password")
            .await
            .unwrap();
        call("add-user", "alice", "synthetic-updated-password")
            .await
            .unwrap();
        assert_eq!(
            call("list-users", "", "").await.unwrap()["users"],
            json!(["admin", "alice"])
        );
        call("add-user", " alice ", "synthetic-spaced-password")
            .await
            .unwrap();
        assert_eq!(
            call("get-stats", " alice ", "").await.unwrap()["stats"][0]["username"],
            " alice "
        );
        call("clear-stats", " alice ", "").await.unwrap();
        call("kill-conn", " alice ", "").await.unwrap();
        call("remove-user", " alice ", "").await.unwrap();
        assert_eq!(
            call("list-users", "", "").await.unwrap()["users"],
            json!(["admin", "alice"])
        );
        let stats = call("get-stats", "alice", "").await.unwrap();
        assert_eq!(stats["stats"][0]["username"], "alice");
        assert_eq!(stats["stats"][0]["tcp_sent"], 0);
        assert_eq!(
            call("get-stats", "", "").await.unwrap()["stats"]
                .as_array()
                .unwrap()
                .len(),
            2
        );
        call("clear-stats", "alice", "").await.unwrap();
        call("clear-stats", "", "").await.unwrap();
        call("kill-conn", "alice", "").await.unwrap();
        call("remove-user", "alice", "").await.unwrap();
        assert!(
            call("get-stats", "alice", "")
                .await
                .unwrap_err()
                .contains("NotFound")
        );
        assert_eq!(
            call("list-users", "", "").await.unwrap()["users"],
            json!(["admin"])
        );
        server.shutdown().await.unwrap();
    });
    runtime.shutdown_timeout(Duration::from_secs(1));
    assert!(crate::SESSION.lock().unwrap().is_none());
}

#[test]
fn remote_commands_work_against_loopback_sunnyquic_with_verified_tls() {
    use shadowquic::{config::SunnyQuicServerCfg, sunnyquic::inbound::SunnyQuicServer};
    struct Cleanup(std::path::PathBuf);
    impl Drop for Cleanup {
        fn drop(&mut self) {
            let _ = std::fs::remove_dir_all(&self.0);
        }
    }
    let folder = Cleanup(std::env::temp_dir().join(
        format!("squirrel-sunny-test-{}-{}", std::process::id(),
        std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()),
    ));
    std::fs::create_dir_all(&folder.0).unwrap();
    let rcgen::CertifiedKey { cert, signing_key } =
        rcgen::generate_simple_self_signed(vec!["localhost".into()]).unwrap();
    let cert_path = folder.0.join("cert.pem");
    let key_path = folder.0.join("key.pem");
    std::fs::write(&cert_path, cert.pem()).unwrap();
    std::fs::write(&key_path, signing_key.serialize_pem()).unwrap();
    let port = std::net::UdpSocket::bind("127.0.0.1:0")
        .unwrap()
        .local_addr()
        .unwrap()
        .port();
    let addr = format!("127.0.0.1:{port}");
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .worker_threads(2)
        .build()
        .unwrap();
    runtime.block_on(async {
        let server = SunnyQuicServer::new(SunnyQuicServerCfg {
            tag: "server".into(),
            bind_addr: addr.parse().unwrap(),
            users: vec![AuthUser {
                username: "admin".into(),
                password: "synthetic-test-password".into(),
            }],
            server_name: "localhost".into(),
            cert_path: cert_path.clone(),
            key_path,
            alpn: vec!["h3".into()],
            gso: false,
            zero_rtt: false,
            ..Default::default()
        })
        .await
        .unwrap();
        server.init().await.unwrap();
        let mut outbound = shadow(&addr, "admin");
        outbound["type"] = json!("sunnyquic");
        outbound["cert-path"] = json!(cert_path);
        let text = profile(outbound);
        let call = |operation: &'static str, user: &'static str, password: &'static str| {
            let cfg = parse(&text).unwrap();
            async move {
                tokio::time::timeout(
                    Duration::from_secs(5),
                    execute(cfg, "server", operation, user, password),
                )
                .await
                .unwrap()
            }
        };
        call("add-user", "alice", "synthetic-user-password")
            .await
            .unwrap();
        assert_eq!(
            call("list-users", "", "").await.unwrap()["users"],
            json!(["admin", "alice"])
        );
        assert_eq!(
            call("get-stats", "alice", "").await.unwrap()["stats"][0]["username"],
            "alice"
        );
        call("get-stats", "", "").await.unwrap();
        call("clear-stats", "alice", "").await.unwrap();
        call("clear-stats", "", "").await.unwrap();
        call("kill-conn", "alice", "").await.unwrap();
        call("remove-user", "alice", "").await.unwrap();
        server.shutdown().await.unwrap();
    });
    runtime.shutdown_timeout(Duration::from_secs(1));
}
