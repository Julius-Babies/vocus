> [!WARNING]
> Vocus is deprecated and replaced by the native [werkbank.CLI](https://github.com/Julius-Babies/werkbank-cli).

# Vocus

**Vocus** is a CLI tool that helps you manage complex software projects with multiple interdependent services. It was created to solve a common pain: when working on applications composed of multiple repositories (e.g. backend, website, auth-server), developers often struggle with setting up a consistent development environment.

Switching between running some services in an IDE, others via Docker, and reconfiguring reverse proxies or ports quickly becomes frustrating. **Vocus** streamlines this process by letting you define your project in a single `Vocusfile` and manage service lifecycles with simple commands.

---

## Features

* **Module-based architecture**: each service (e.g. `auth`, `backend`, `website`) is defined as a *module*.
* **Flexible service states**:

    * `Off` → the module is disabled.
    * `Local` → Vocus routes traffic to your locally running dev instance (e.g. IDE).
    * `Docker` → Vocus starts the service in a Docker container with predefined routing.
* **Built-in infrastructure management**: define databases and other dependencies in the `Vocusfile`.
* **Unified networking**: services are automatically reachable under `<module>.<project>.local.vocus.dev`. You can also add extra subdomains to your module.
* **Declarative configuration** with a simple YAML format.

---

## Example `Vocusfile`

```yaml
name: Example

infrastructure:
  databases:
    - type: postgres
      version: 16
      databases:
        - main
        - secondary

modules:
  whoami:
    image: "traefik/whoami"
    routes:
      - ports:
          local: 8888
          docker: 80
        path_prefixes:
          - /
```

---

## Installation

Vocus is implemented in **Kotlin** and runs on the JVM.

```bash
java -jar vocus.jar
# reload your shell afterwards
```

Once installed, the `vocus` command will be available.

---

## Usage

### Register a project

```bash
vocus project register --vocusfile ./Vocusfile
```

or run `vocus project register` in an existing project directory containing a `Vocusfile`.

### Start a project

```bash
vocus project <name> up
```

### Manage module states

```bash
vocus project <name> module <module-name> set-state Off
vocus project <name> module <module-name> set-state Local
vocus project <name> module <module-name> set-state Docker
```

Examples:

* Disable `auth` while working only on the landing page:

  ```bash
  vocus project myapp module auth set-state Off
  ```
* Route `auth` traffic to your IDE instance:

  ```bash
  vocus project myapp module auth set-state Local
  ```
* Run `auth` as a Docker container:

  ```bash
  vocus project myapp module auth set-state Docker
  ```

---

## Why Vocus?

Without Vocus:

* You manually start and stop containers.
* You set up reverse proxies or change IPs/ports in your app.
* You constantly switch between IDE, Docker, and environment configs.

With Vocus:

* One declarative file (`Vocusfile`) defines your project.
* You switch module states with a single command.
* Local and containerized services integrate seamlessly.

---

## Roadmap

> [!NOTE]
> Vocus is currently in early development. Nearly all features are subject to change.

* Improved multi-project management.
* Additional infrastructure providers.
* GUI/TUI for easier module state switching.


