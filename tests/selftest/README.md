# EDT editing self test

Bundle `org.mard.dt.editing.selftest` that runs inside a live 1C:EDT instance and
reports what the editing restriction plugin answers for every metadata object of
a project.

The self test calls the real EDT API (`IModelEditingSupportProvider.canEdit`,
the same entry point EDT uses) plus the plugin service itself, so the report
reflects what the user really gets in the UI.

## Build and install

```powershell
powershell -ExecutionPolicy Bypass -File tests\selftest\build.ps1 -Install
```

The script compiles the bundle against the EDT installation
(`C:\Program Files\1C\1CE\components\1c-edt-2026.2.2+21-x86_64` by default, see
`-Edt`), using the Java 25 JDK shipped with 1C:EDT, and writes
`target\org.mard.dt.editing.selftest_<version>.jar`.

The class path includes the installed `org.mard.dt.editing_0.7.*` bundle from the
user Eclipse pool, so build 0.7.1 must be installed before the self test is built.

The bundle is not published to the update site. With `-Install` the jar is copied
into the `dropins` folder of the EDT installation and EDT is restarted manually.

Note: when the bundle content changes, bump `Bundle-Version` in
`META-INF\MANIFEST.MF`. Equinox keeps the resolved revision of a bundle in the
configuration area, so a rebuilt jar with the same version is not picked up.

## Run

Start EDT with the project that has to be checked:

```
1cedt.exe -data <workspace> -vm "<jdk25>\bin\server\jvm.dll" --launcher.appendVmargs -vmargs -Dosgi.requiredJavaVersion=25 -Dorg.mard.dt.editing.selftest.enabled=true -Dorg.mard.dt.editing.selftest.project=<project>
```

The self test does nothing unless `-Dorg.mard.dt.editing.selftest.enabled=true`
is set, so the bundle stays inert in every other EDT instance.

Optional properties:

- `org.mard.dt.editing.selftest.project` — project name, default `cf`.
- `org.mard.dt.editing.selftest.report` — report file, default
  `<workspace>\edt-editing-selftest.tsv`.

The report is a TSV file, one row per metadata object:

```
# type  bmFqn  ourFqn  path  provider(OTHER)  content  create  structural  other
```

- `type` — EMF type of the object.
- `bmFqn` — FQN reported by the BM model.
- `ourFqn` — FQN computed by the plugin (`IPathEditingService.getObjectFullName`).
- `path` — project relative file path of the object.
- `provider(OTHER)` — result of `IModelEditingSupportProvider.canEdit(DIRECT)`.
  The column is named after the call kind the bundle assumes, `OTHER`: the real
  UI action is not known outside of an interaction, so a `false` here means the
  object is closed for a neutral check, not for a specific menu command.
- `content`, `create`, `structural`, `other` — result of
  `IPathEditingService.canEdit` for every edit call kind.

`provider(OTHER)` and `content` differ when EDT asks for a different call kind
than `CONTENT`, which is the usual reason why an object looks editable although
the rule says otherwise.
