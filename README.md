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
- `Add to enable.fullname` - append the FQN to `enable.fullname` in `<project>/.settings/editing.yml`;
- `Refresh editing restrictions` - re-read the rules without restart (the file is not modified).

The commands only append entries to the chosen list (an existing identical entry is a no-op) and preserve the rest of the file: comments, unknown fields, order, encoding and line endings.

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

The `Object` submenu is available for any metadata object, the module submenus only for the objects that have such a module.

### Sub-objects

For attributes, tabular sections, enum values, journal columns, commands, templates and external data source objects (tables, cubes, dimension tables, functions, fields, dimensions, resources) a second `Editing Restrictions` menu offers only `Copy FQN` and `Add to enable.fullname`: such an element is enabled pointwise, the owner object stays locked.

The FQN of a sub-object is built as the owner FQN plus the element name plus the type marker: `Catalog.Products.Barcode.Attribute`, `Catalog.Products.Goods.TabularSection`, `Enum.Statuses.New.Value`, `DocumentJournal.Orders.Quantity.Column`, `Task.Performer.Do.Command`, `Catalog.Products.Price.Template`, `ExternalDataSource.Source.Cube.Cube1.Field.Field`.

### Semantics and limitations

- `enable` rules win over `disable` rules; a configuration with `enable` only has no effect (there must be something to disable).
- A rule with a type marker never unlocks the whole file: it applies to that element only.
- Enabling a sub-object does not open the owner itself: its `Main` page, properties and help stay read-only.
- Structural operations (create, delete, move, sort) require an enabled owner. The new object wizard checks the top owner object, so while a task or a cube is locked you cannot add a new attribute even into an enabled tabular section.
- Deleting children of a disabled object is not allowed.
- Disabled objects are skipped from project validation by 1C:EDT.

### Other commands

- `Sort settings` in the project properties (`Disable editing` section) sorts the `fullname` and `path` lists of the `disable` and `enable` sections case-insensitively, keeping the rest of the file untouched.
- `Refresh editing restrictions` re-reads `<project>/.settings/editing.yml` without restart.

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

# Плагин запрета редактирования для 1C:EDT

Плагин для 1C:Enterprise Development Tools блокирует редактирование объектов метаданных 1С в редакторах в интерфейсе. Гибкие настройки блокируемых объектов.

Исторически, разработчики использовали настройки поддержки поставщика чтобы разрешить редактирование некоторых объектов метаданных, а остальные объекты оставить заблокированными (типа на поддержке поставщика Конфигурации).

В новой IDE 1C:EDT  приложения разрабатываются гибче, в т.ч. используя Git для ветвления supplier-master, начальные настройки поставки можно при этом использовать, но добавляют сложности больше чем помощи. Поэтому большинство разработчиков  удаляют настройки из репозитория.

Разрабочики желают защитить себя или более молодых коллег от случайного редактирования некоторых объектов, которые бы не хотели редактировать в процессе изучения больших Конфигураций.

В Плагине запрета редактирования разработчик настраивает некоторые правила, чтобы включить режим Только-просмотр в редакторах интерфейса 1C:EDT.


## Установка


| Версия плагина | Версия 1C:EDT |
|----------------|---------------|
| [0.6.0](https://gitlab.com/marmyshev/edt-editing/-/packages/) | 2024.1+ и новее... |
| [0.5.0](https://gitlab.com/marmyshev/edt-editing/-/packages/15488906) | 2023.1 - 2023.3 |
| [0.4.0](https://gitlab.com/marmyshev/edt-editing/-/packages/10924902) | 2020.5 - 2022.2 |
| 0.3.0 | 2020.5+ и новее... |
| 0.2.0 | 2020.5 - 2020.6 |
| 0.1.0 | 2020.1 - 2020.4 |


[![Перетяните это в *запущенный воркспейс EDT*. Требуется клиент Маркетплейс Эклипса](https://marketplace.eclipse.org/sites/all/themes/solstice/public/images/marketplace/btn-install.png)](http://marketplace.eclipse.org/marketplace-client-intro?mpc_install=5141319 "Перетяните это в *запущенный воркспейс EDT*. Требуется клиент Маркетплейс Эклипса")


Для ручной установки Выбеите меню: `Help -> Install New Software...` вставьте адрес p2-репозитория [https://marmyshev.gitlab.io/edt-editing/update](https://marmyshev.gitlab.io/edt-editing/update)

Выберите фичу для установки `Disable Editing plugin for 1C:EDT`


## Основные возможности

Настройка правил блокировки ( **Disable** ) редактирования (режим Только-просмотр) в редакторах:

- Указание подсистем, состав объектов которых заблокирован для редактирования, иерархия подсистем поддерживается
- Указание относительно проекта полного пути к файлу или к каталогу, контент которых заблокирован для редактирования
- Указание полного квалифицированного имени метаданного 1С:Предприятия (хранящиеся в отдельных файлах), которые должны быть заблокированы в редакторах
- Указание серверных веток Git, список файлов в которых можно заблокировать для редактирования

Настройка правила исключения ( **Enable** )  для разрешения редактирования:

- по подсистеме
- по пути к файлу или каталогу относительно проекта
- по полному квалифицированному имени


> Примечание! Этот плагин не  блокирует редактирование файлов через файловую систему!

**Внимание!**  Заблокированные объекты будут исключены из проверки (валидации) по проекту в 1C:EDT.


##  Запрет редактирования в дереве конфигурации

В контекстном меню дерева конфигурации (одиночный выбор) используйте меню `Запрет редактирования`, чтобы управлять правилами редактирования выбранного объекта:

- `Копировать FQN` - скопировать в буфер полное квалифицированное имя 1C выбранного объекта, его модуля или модуля его формы;
- `Добавить в disable.fullname` - дописать FQN в `disable.fullname` файла `<проект>/.settings/editing.yml`;
- `Добавить в enable.fullname` - дописать FQN в `enable.fullname` того же файла;
- `Обновить правила запрета` - перечитать правила без перезапуска EDT (файл не изменяется).

Команды только дописывают записи в выбранный список (повторная такая же запись - ничего не делает) и сохраняют остальное содержимое файла: комментарии, неизвестные поля, порядок, кодировку и переводы строк.

### Цели

Подменю показываются только если у объекта действительно есть соответствующий модуль:

| Подменю | Пример FQN |
|---------|-------------|
| Объект | `Catalog.Products` |
| Модуль объекта | `Catalog.Products.ObjectModule` |
| Модуль менеджера | `Catalog.Products.ManagerModule` |
| Модуль набора записей | `InformationRegister.Products.RecordSetModule` |
| Форма | `Catalog.Products.Form.ItemForm.Form` |
| Модуль формы | `Catalog.Products.Form.ItemForm.Form.Module` |

Подменю `Объект` доступно для любого объекта метаданных, модульные подменю - только для тех объектов, у которых такой модуль есть.

### Под-объекты

Для реквизитов, табличных частей, значений перечислений, граф журнала документов, команд, макетов и объектов внешних источников данных (таблицы, кубы, таблицы измерений, функции, поля, измерения, ресурсы) в дереве появляется второе меню `Запрет редактирования` с пунктами только `Копировать FQN` и `Добавить в enable.fullname`: такой элемент разрешается точечно, владелец остается заблокированным.

FQN под-объекта строится как FQN владельца + имя элемента + маркер типа: `Catalog.Products.Barcode.Attribute`, `Catalog.Products.Goods.TabularSection`, `Enum.Statuses.New.Value`, `DocumentJournal.Orders.Quantity.Column`, `Task.Performer.Do.Command`, `Catalog.Products.Price.Template`, `ExternalDataSource.Source.Cube.Cube1.Field.Field`.

### Семантика и ограничения

- Правила `enable` важнее правил `disable`; конфигурация только с `enable` не действует (блокировать должно быть что-то).
- Правило с маркером типа никогда не разблокирует файл целиком - оно действует только на этот элемент.
- Включение под-объекта не открывает самого владельца: его вкладка `Основное`, свойства и справка остаются только для чтения.
- Структурные операции (создание, удаление, перемещение, сортировка) требуют разрешенного владельца. Мастер создания нового объекта проверяет верхний объект, поэтому пока задача или куб заблокированы, нельзя добавить новый реквизит даже в разрешенную табличную часть.
- Удалять дочерние элементы заблокированного объекта нельзя.
- Заблокированные объекты исключаются из проверки (валидации) по проекту в 1C:EDT.

### Прочие команды

- `Отсортировать настройки` в свойствах проекта (раздел `Запрет редактирования`) сортирует списки `fullname` и `path` секций `disable` и `enable` без учета регистра, не трогая остальное содержимое файла.
- `Обновить правила запрета` перечитывает `<проект>/.settings/editing.yml` без перезапуска EDT.

### Отладка

Запустите 1C:EDT с ключом `-Dorg.mard.dt.editing.debug=true` (после `-vmargs` в `eclipse.ini`), чтобы в лог попадали уникальные решения о редактировании, или с `-Dorg.mard.dt.editing.debug.all=true` - чтобы логировалась каждая проверка. Сообщения `canEdit call:`, `canEdit sub-object:`, `canEdit path:` в файле `.log` рабочей области показывают объект, сработавшее правило и результат.


##  Настройка правил по проекту


Разместите Ямл файл с настройками в каталоге вашего проектта: `YourProjectName/.settings/editing.yml` с содержанием:


```yaml
# секция блокирирует редактирование в UI
disable:
  # Список подсистем, состав объектов которых должен быть заблокирован
  subsystem:
    - DisabledSubsystem1
    - DisabledSubsystem2
  # Список путей относительно проекта к файлам или каталогам
  path:
    - src/CommonModules/LockedOnlyModule/Module.bsl
   
  # Список полных квалифицированных имен объектов с ресурсами
  fullname:
    - Catalog.Products.Form.ItemForm.Form

  # Список серверных веток Git контент которых должен быть заблокирован
  branch:
    - vendor
   
# секция добавляет исключения из правил блокировки
enable:
  merge: true # разрешает редактирование из системы Саравнения/Объединения
  subsystem:
    - EnabledSubsystem
  path:
    - src/Catalogs/Enabled

```

### Создайте или откройте настройки в Редакторе проекта

Откройте "Редактор проекта" в меню Навигатора 1С.

Создайте новый файл настроек запрета редактирования и продолжите настройку вашего проекта.

![image](images/create_settings.png "Создайте новые настройки запрета редактирования")

Или откройте существующий файл настроект запрета редактирования чтобы настроить ваш проект.

![image](images/open_settings.png "Откройте существующий файл настроек")


## Демо пример

### Простой пример для блокировки всего проекта

```yaml
# секция блокирирует редактирование в UI
disable:
  # Список путей относительно проекта к файлам или каталогам
  path:
    - src/
```

Откройте 1C:EDT и импортируйте проект из этого репозитория [EditingDemoConfig](EditingDemoConfig) в воркспейс.

[Демо настройки здесь](EditingDemoConfig/.settings/editing.yml).

Откройте объекты метаданных, чтобы исследовать функциональность блокировки и разрешения!

> Примечание! Если установка плагина была выполнена после создания проекта (воркспейса), то, для отображения иконок блокированных объектов в навигаторе, необходимо включить отображение значков в настройках "Окно -> Праметры -> Внешний вид -> Оформление меток" , пункт "Disable to edit objects"

![image](images/settings_screen.png "Параметры меток")
