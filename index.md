---
---

# Disable Editing plugin for 1C:EDT

> Russian description goes after English

> Русское описание идет после английского

Plugin for 1C:Enterprise Development Tools disables editing of 1C metadata objects in UI editors. Reach settings of blocked objects.

Historically developers used supplier's settings to enable editing of some metadata objects and the rest of objects keep disabled (aka supplied by Configuration vendor).

In new IDE 1C:EDT application develops more flexible and uses git for supplier-master branch workflow, initial supplier's settings now can be still used but give more tricks than more help. So most developers remove them from repository.

Developers want to protect themselves or their juniors from accidental editing some object that they didn't want to edit while exploring reach Configurations.

Disable Editing plugin developer set up some rules to make read-only mode in UI editors of 1C:EDT.


## Install


| Plugin Version | 1C:EDT version |
|----------------|----------------|
| [0.6.0](https://gitlab.com/marmyshev/edt-editing/-/packages/) | 2024.1+ and newer... |
| [0.5.0](https://gitlab.com/marmyshev/edt-editing/-/packages/15488906) | 2023.1 - 2023.3 |
| [0.4.0](https://gitlab.com/marmyshev/edt-editing/-/packages/10924902) | 2020.5 - 2022.2 |
| 0.3.0 | 2020.5+ and newer... |
| 0.2.0 | 2020.5 - 2020.6 |
| 0.1.0 | 2020.1 - 2020.4 |


[![Drag to your running 1C:EDT* workspace. *Requires Eclipse Marketplace Client](https://marketplace.eclipse.org/sites/all/themes/solstice/public/images/marketplace/btn-install.png)](http://marketplace.eclipse.org/marketplace-client-intro?mpc_install=5141319 "Drag to your running 1C:EDT* workspace. *Requires Eclipse Marketplace Client")


For manual install Select menu: `Help -> Install New Software...` past in p2-repository URL [https://marmyshev.gitlab.io/edt-editing/update](https://marmyshev.gitlab.io/edt-editing/update)

Select installable feature `Disable Editing plugin for 1C:EDT`


## Main features

Set up rules to **Disable** editing (read-only mode) in editors:

- specify subsystems which content objects are disabled to edit, hierarchy of subsystems is supported
- specify project relative full path or folders which content is disabled to edit
- specify full qualified names of 1C:Enterprise metadata objects (that stores in single file) that should be disabled in editors
- specify branch names which content is disabled to edit

Set up rules to **Enable** as exceptions from disable rules:

- by subsystem
- by project relative path to file or to folder
- by full qualified name


> Note! This plugin does not disables editing files via file system!

**Attantion!**  Disabled object will be skipped from project validation by 1C:EDT.


## Editing Restrictions in the configuration tree

In the configuration tree context menu (single selection) use the `Editing Restrictions` menu to manage editing rules of the selected object:

- `Copy FQN` - copy the canonical 1C full name of the selected object, its module or the form module to the clipboard;
- `Add to disable.fullname` - append the FQN to `disable.fullname` in `<project>/.settings/editing.yml`;
- `Add to enable.fullname` - append the FQN to `enable.fullname` in `<project>/.settings/editing.yml`.

The commands only append entries to the chosen list (an existing identical entry is a no-op) and preserve the rest of the file: comments, unknown fields, order, encoding and line endings. The rules are re-read automatically as soon as `editing.yml` changes, so no restart or refresh is needed.

### Targets

Submenus are shown only when the selected object really has the corresponding module:

| Submenu | FQN example |
|---------|-------------|
| Object | `Catalog.Products` |
| Object module | `Catalog.Products.ObjectModule` |
| Manager module | `Catalog.Products.ManagerModule` |
| Record set module | `InformationRegister.Products.RecordSetModule` |
| Form | `Catalog.Products.Form.ItemForm.Form` |
| Form module | `Catalog.Products.Form.ItemForm.Form.Module` |
| Module (common module) | `CommonModule.Locked` / its code `CommonModule.Locked.Module` |
| Configuration | `Configuration` |
| Application module | `Configuration.ManagedApplicationModule` |
| Session module | `Configuration.SessionModule` |
| External connection module | `Configuration.ExternalConnectionModule` |

The `Object` submenu is available for any metadata object, the module submenus only for the objects that have such a module.

For a common module the object itself and its BSL code are controlled separately: `CommonModule.Locked` locks the properties (`Locked.mdo`), `CommonModule.Locked.Module` locks only the code (`Module.bsl`). The same holds for the configuration: `Configuration` locks its properties and command interfaces, while `Configuration.<XxxModule>` locks only the corresponding module file. Enabling `Configuration` opens its `.mdo`, both `.cmi` files and all three modules; enabling a module does not open the configuration itself.

Forms and templates of such containers are regular targets too: `SettingsStorage.Settings.Form.FormT1.Form`, `SettingsStorage.Settings.Template.TemplateT1.Template`.

### Sub-objects

For attributes, tabular sections, enum values, journal columns, commands, templates and external data source objects (tables, cubes, dimension tables, functions, fields, dimensions, resources) a second `Editing Restrictions` menu offers only `Copy FQN` and `Add to enable.fullname`: such an element is enabled pointwise, its own content becomes editable too, but the owner object stays locked.

The FQN of a sub-object is built as the owner FQN plus the element name plus the type marker: `Catalog.Products.Barcode.Attribute`, `Catalog.Products.Goods.TabularSection`, `Enum.Statuses.New.Value`, `DocumentJournal.Orders.Quantity.Column`, `Task.Performer.Do.Command`, `Catalog.Products.Price.Template`, `ExternalDataSource.Source.Cube.Cube1.Field.Field`.

HTTP and web service members follow the same pattern: `HTTPService.Service.URLTemplate.Template.Method.Method1`, `WebService.Service.Operation.Operation1.Parameter.Parameter1`.

Subsystems are separate top objects, not sub-objects: the nested subsystem FQN is `Subsystem.Parent.Subsystem.Child`. Enabling a parent subsystem opens its nested subsystems, and enabling only a nested one works while the parent stays locked.

### Semantics and limitations

- `enable` rules win over `disable` rules; a configuration with `enable` only has no effect (there must be something to disable).
- A rule with a type marker never unlocks the whole file: it applies to that element only.
- An enabled container opens its own content: an enabled tabular section opens its attributes, an enabled object opens its content. The owner itself (its `Main` page, properties, help) stays locked.
- Enabling a sub-object does not open the owner itself: its `Main` page, properties and help stay read-only.
- Structural operations (create, delete, move, sort) require an enabled owner. The new object wizard checks the top owner object, so while a task or a cube is locked you cannot add a new attribute even into an enabled tabular section.
- Deleting children of a disabled object is not allowed.
- Common attributes (`Общие реквизиты`) are separate global metadata objects with their own files (`src/CommonAttributes/<Name>/<Name>.mdo`) shared between owners. Enabling an object does not open the common attributes attached to it: enable them separately, by path `src/CommonAttributes` or by their own full name.
- Disabled objects are skipped from project validation by 1C:EDT.
- A configuration module is controlled separately from the configuration: `Configuration.SessionModule` locks only `SessionModule.bsl`, `Configuration` locks the configuration object with its command interfaces and all its modules.

### Other commands

- `Sort settings` in the project properties (`Disable editing` section) sorts the `fullname` and `path` lists of the `disable` and `enable` sections case-insensitively, keeping the rest of the file untouched.

### Debugging

Start 1C:EDT with `-Dorg.mard.dt.editing.debug=true` (after `-vmargs` in `eclipse.ini`) to log unique editing decisions, or with `-Dorg.mard.dt.editing.debug.all=true` to log every check. The messages `canEdit call:`, `canEdit sub-object:`, `canEdit path:` in the workspace `.log` show the object, the applied rule and the result.


## Set up your project rules

Place Yaml file in your project settings folder: `YourProjectName/.settings/editing.yml` with content:


```yaml
# section allows to disable editing in UI
disable:
  # List of subsystems which content should be disabled
  subsystem:
    - DisabledSubsystem1
    - DisabledSubsystem2
  # List of project relative path to file or to folder 
  path:
    - src/CommonModules/LockedOnlyModule/Module.bsl
   
  # Full qualified name of top objects (resources)
  fullname:
    - Catalog.Products.Form.ItemForm.Form

  # List of Git remote branches which content should be disabled
  branch:
    - vendor
     
# section allows to make some exceptions from disable rule
enable:
  merge: true # allows to edit by compare and merge EDT system
  subsystem:
    - EnabledSubsystem
  path:
    - src/Catalogs/Enabled

```

### Create or open settings in Project editor

Open "Project editor" in menu of 1C Navigator.

Create new disable editing settings file and continue to setup your project.

![image](images/create_settings.png "Create new disable editing settings")

Or open existing disable editing settings file to setup your project.

![image](images/open_settings.png "Open existing settings file")

##  Demo example

### Simple example for disabling an entire project

```yaml
# section allows to disable editing in UI
disable:
  # List of project relative path to file or to folder 
  path:
    - src/
```

Open 1C:EDT and import project from this repository [EditingDemoConfig](EditingDemoConfig) into workspace.

[Demo settings here](EditingDemoConfig/.settings/editing.yml).

Open metadata objects to check out disabling and enabling features!

> Note! If the plugin was installed after the creation of the workspace then, to display the icons of blocked objects in the navigator, you need to enable display the icon in the settings "Window -> Parameters -> Appearance -> Label decoration", item "Disable to edit objects"

# Плагин Disable Editing для 1C:EDT

Плагин для 1C:Enterprise Development Tools: запрещает редактирование объектов метаданных 1С в редакторах IDE и даёт доступ к настройкам заблокированных объектов.

Исторически разработчики пользовались настройками поставщика, чтобы разрешить редактирование части объектов, а остальные оставались недоступными (как объекты, поставленные вендором конфигурации).

В новой IDE 1C:EDT разработка стала гибче и использует git с веткой supplier-master, поэтому настройки поставщика сейчас приносят больше проблем, чем пользы. Большинство разработчиков удаляют их из репозитория.

Разработчики хотят защитить себя и младших коллег от случайного изменения объектов, которые не нужно трогать при изучении чужих конфигураций.

Плагин Disable Editing позволяет задать правила и включить режим только для чтения в редакторах 1C:EDT.

## Установка

Для ручной установки выберите меню `Help -> Install New Software...` и укажите p2-репозиторий [https://pravets.github.io/edt-editing/update](https://pravets.github.io/edt-editing/update), затем выберите компонент `Disable Editing plugin for 1C:EDT`.

## Основные возможности

Правила **disable** (запрет редактирования, режим только для чтения):

- подсистемы, содержимое которых запрещено редактировать (поддерживается иерархия подсистем);
- путь или папки проекта, содержимое которых запрещено редактировать;
- полные имена (FQN) объектов метаданных 1С, которые хранятся в отдельном файле и должны быть запрещены в редакторах;
- имена git-веток, содержимое которых запрещено редактировать.

Правила **enable** (исключения из запретов):

- по подсистеме;
- по пути к файлу или папке проекта;
- по полному имени (FQN).

> Важно! Плагин не запрещает редактирование файлов средствами файловой системы.

**Внимание!** Запрещённые объекты исключаются из проверки проекта в 1C:EDT.

## Запрет редактирования в дереве конфигурации

В контекстном меню дерева конфигурации (при выборе одного объекта) используйте меню `Запрет редактирования`:

- `Копировать FQN` — копирует в буфер обмена каноническое полное имя объекта, его модуля или модуля формы;
- `Добавить в disable.fullname` — добавляет FQN в `disable.fullname` файла `<project>/.settings/editing.yml`;
- `Добавить в enable.fullname` — добавляет FQN в `enable.fullname` файла `<project>/.settings/editing.yml`.

Команды только добавляют строку в выбранный список (повтор существующей записи ничего не делает) и сохраняют остальное содержимое файла: комментарии, неизвестные поля, порядок, кодировку и переводы строк. Правила перечитываются автоматически при изменении `editing.yml` — перезапуск или обновление не нужны.

### Объекты

Подменю показываются только тогда, когда у выбранного объекта действительно есть соответствующий модуль:

| Подменю | Пример FQN |
|---------|------------|
| Объект | `Catalog.Products` |
| Модуль объекта | `Catalog.Products.ObjectModule` |
| Модуль менеджера | `Catalog.Products.ManagerModule` |
| Модуль набора записей | `InformationRegister.Products.RecordSetModule` |
| Форма | `Catalog.Products.Form.ItemForm.Form` |
| Модуль формы | `Catalog.Products.Form.ItemForm.Form.Module` |
| Модуль (общий модуль) | `CommonModule.Locked` / его код `CommonModule.Locked.Module` |
| Конфигурация | `Configuration` |
| Модуль приложения | `Configuration.ManagedApplicationModule` |
| Модуль сеанса | `Configuration.SessionModule` |
| Модуль внешнего соединения | `Configuration.ExternalConnectionModule` |

Подменю `Объект` доступно для любого объекта метаданных, подменю модулей — только для объектов, у которых такой модуль есть.

У общего модуля сам объект и его BSL-код управляются раздельно: `CommonModule.Locked` запрещает свойства (`Locked.mdo`), `CommonModule.Locked.Module` запрещает только код (`Module.bsl`). То же для конфигурации: `Configuration` запрещает саму конфигурацию и её командные интерфейсы, а `Configuration.<XxxModule>` — только соответствующий модуль. Включение `Configuration` открывает `.mdo`, оба `.cmi` и все три модуля; включение модуля не открывает саму конфигурацию.

Формы и макеты таких контейнеров — обычные цели: `SettingsStorage.Settings.Form.FormT1.Form`, `SettingsStorage.Settings.Template.TemplateT1.Template`.

### Под-объекты

Для реквизитов, табличных частей, значений перечислений, колонок журнала, команд, макетов и объектов внешних источников данных (таблицы, кубы, таблицы измерений, функции, поля, измерения, ресурсы) второе меню `Запрет редактирования` предлагает только `Копировать FQN` и `Добавить в enable.fullname`: такой элемент разрешается точечно, его собственное содержимое тоже становится доступным, а владелец остаётся закрытым.

FQN под-объекта строится как FQN владельца плюс имя элемента плюс маркер типа: `Catalog.Products.Barcode.Attribute`, `Catalog.Products.Goods.TabularSection`, `Enum.Statuses.New.Value`, `DocumentJournal.Orders.Quantity.Column`, `Task.Performer.Do.Command`, `Catalog.Products.Price.Template`, `ExternalDataSource.Source.Cube.Cube1.Field.Field`.

Методы HTTP- и web-сервисов устроены так же: `HTTPService.Service.URLTemplate.Template.Method.Method1`, `WebService.Service.Operation.Operation1.Parameter.Parameter1`.

Подсистемы — самостоятельные top-объекты, а не под-объекты: FQN вложенной подсистемы `Subsystem.Parent.Subsystem.Child`. Включение родительской подсистемы открывает вложенные, а включение только вложенной работает при запрещённой родительской.

### Семантика и ограничения

- Правила `enable` приоритетнее `disable`; конфигурация только с `enable` не работает (должно быть что запрещать).
- Правило с маркером типа никогда не открывает файл целиком: оно действует только на этот элемент.
- Включённый контейнер открывает своё содержимое: включённая табличная часть открывает свои реквизиты, включённый объект — своё содержимое. Сам владелец (страница `Основные`, свойства, справка) остаётся закрытым.
- Включение под-объекта не открывает владельца: его страница `Основные`, свойства и справка остаются только для чтения.
- Структурные операции (создание, удаление, перемещение, сортировка) требуют включённого владельца. Мастер создания нового объекта проверяет верхний объект-владелец, поэтому при запрещённой задаче или кубе нельзя добавить новый реквизит даже во включённую табличную часть.
- Удаление дочерних объектов запрещённого объекта не разрешено.
- Общие реквизиты — отдельные глобальные объекты метаданных со своими файлами `src/CommonAttributes/<Имя>/<Имя>.mdo`, общими для владельцев. Включение объекта не открывает привязанные к нему общие реквизиты: включайте их отдельно — по пути `src/CommonAttributes` или по их собственному полному имени.
- Запрещённые объекты исключаются из проверки проекта 1C:EDT.
- Модуль конфигурации управляется отдельно от конфигурации: `Configuration.SessionModule` запрещает только `SessionModule.bsl`, `Configuration` — саму конфигурацию с командными интерфейсами и всеми модулями.

### Прочие команды

- `Отсортировать настройки` в свойствах проекта (раздел `Запрет редактирования`) сортирует списки `fullname` и `path` секций `disable` и `enable` без учёта регистра, не трогая остальное содержимое файла.

### Отладка

Запустите 1C:EDT с `-Dorg.mard.dt.editing.debug=true` (после `-vmargs` в `eclipse.ini`), чтобы логировать уникальные решения о редактировании, или с `-Dorg.mard.dt.editing.debug.all=true`, чтобы логировать каждую проверку. Сообщения `canEdit call:`, `canEdit sub-object:`, `canEdit path:` в `.log` рабочего пространства показывают объект, применённое правило и результат.

## Настройка правил проекта

Разместите Yaml-файл в папке настроек проекта: `ИмяПроекта/.settings/editing.yml`:

```yaml
# section allows to disable editing in UI
disable:
  # List of subsystems which content should be disabled
  subsystem:
    - DisabledSubsystem1
    - DisabledSubsystem2
  # List of project relative path to file or to folder 
  path:
    - src/CommonModules/LockedOnlyModule/Module.bsl
   
  # Full qualified name of top objects (resources)
  fullname:
    - Catalog.Products.Form.ItemForm.Form

  # List of Git remote branches which content should be disabled
  branch:
    - vendor
     
# section allows to make some exceptions from disable rule
enable:
  merge: true # allows to edit by compare and merge EDT system
  subsystem:
    - EnabledSubsystem
  path:
    - src/Catalogs/Enabled

```

### Создание и открытие настроек в редакторе проекта

Откройте "Редактор проекта" в меню навигатора 1C.

Создайте новый файл настроек запрета редактирования и продолжайте настройку проекта.

![image](images/create_settings.png "Create new disable editing settings")

Или откройте существующий файл настроек.

![image](images/open_settings.png "Open existing settings file")

## Демо-пример

### Простой пример запрета редактирования всего проекта

```yaml
# section allows to disable editing in UI
disable:
  # List of project relative path to file or to folder 
  path:
    - src/
```

Откройте 1C:EDT и импортируйте проект из этого репозитория [EditingDemoConfig](EditingDemoConfig) в рабочее пространство.

[Настройки демо здесь](EditingDemoConfig/.settings/editing.yml).

Откройте объекты метаданных и проверьте запрет и разрешение редактирования!

> Примечание! Если плагин установлен после создания рабочего пространства, для отображения иконок заблокированных объектов в навигаторе включите отображение иконки в настройках "Окно -> Параметры -> Внешний вид -> Оформление меток", пункт "Запретить редактирование объектов".
