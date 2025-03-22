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
              metals
              unzip
              python3
            ];
          };
        }
    );
}
