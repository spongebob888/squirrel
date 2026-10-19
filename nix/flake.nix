{
  description = "Squirrel Android VPN development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    rust-overlay.url = "github:oxalica/rust-overlay";
    rust-overlay.inputs.nixpkgs.follows = "nixpkgs";
  };

  outputs =
    { nixpkgs, rust-overlay, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        overlays = [ rust-overlay.overlays.default ];
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
      buildToolsVersion = "36.0.0";
      ndkVersion = "28.2.13676358";
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "36" ];
        buildToolsVersions = [ buildToolsVersion ];
        includeNDK = true;
        ndkVersions = [ ndkVersion ];
        includeEmulator = true;
        includeSystemImages = true;
        systemImageTypes = [ "google_apis" ];
        abiVersions = [ "x86_64" ];
      };
      sdkRoot = "${android.androidsdk}/libexec/android-sdk";
      rust = pkgs.rust-bin.stable.latest.default.override {
        extensions = [
          "rustfmt"
          "clippy"
        ];
        targets = [
          "aarch64-linux-android"
          "x86_64-linux-android"
        ];
      };
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          android.androidsdk
          pkgs.jdk21
          rust
          pkgs.python3
          pkgs.pkg-config
          pkgs.gnumake
          pkgs.git
          pkgs.curl
          pkgs.unzip
          pkgs.util-linux
          pkgs.shellcheck
          pkgs.nixfmt
        ];

        SQUIRREL_NIX_ENV = "1";
        SQUIRREL_SYSTEM_IMAGE = "system-images;android-36;google_apis;x86_64";
        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        ANDROID_NDK_HOME = "${sdkRoot}/ndk/${ndkVersion}";
        JAVA_HOME = "${pkgs.jdk21}";
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";
      };

      formatter.${system} = pkgs.nixfmt;
      checks.${system}.format =
        pkgs.runCommand "squirrel-nix-format"
          {
            nativeBuildInputs = [ pkgs.nixfmt ];
          }
          ''
            nixfmt --check ${./flake.nix}
            touch "$out"
          '';
    };
}
