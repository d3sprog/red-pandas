{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs?ref=nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };
  outputs = { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
      in
        {
          devShells.default = pkgs.mkShell {
            nativeBuildInputs = with pkgs; [
              (pkgs.sbt.override {
                jre = pkgs.graalvmPackages.graalvm-ce;
              })
              # graalvmPackages.graalvm-ce
              (pkgs.metals.override {
                jre = pkgs.graalvmPackages.graalvm-ce;
              })
              openjdk
              zlib
              unzip
              python3
              python3Packages.pandas
              python3Packages.numpy
              python3Packages.jupyter
            ];
          };
        }
    );
}
