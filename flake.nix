{
  description = "pa1-template";

  inputs = {
    alr-pa-env.url = "github:cs389l-web/alr-pa-env";
  };

  outputs = {
    self,
    alr-pa-env,
  }: {
    devShells =
      alr-pa-env.inputs.nixpkgs.lib.mapAttrs
      (_system: _shells: {default = alr-pa-env.devShells.${_system}.pa1;})
      alr-pa-env.devShells;

    packages =
      alr-pa-env.inputs.nixpkgs.lib.mapAttrs
      (_system: _packages: {dockerImage = alr-pa-env.packages.${_system}.alr-java;})
      alr-pa-env.packages;
  };
}
