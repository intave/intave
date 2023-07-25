# General overview of code structure

## Checks
Checks provide a skeletal architecture for both detection algorithms and detection clusters.<br>
They are constrained to the `intave/check` package and have to extend the Check class.<br>
They are the only place where the actual detection logic is implemented.<br>
Each check has a class and an equally-named subpackage with check-parts or required components.

So for example the `intave/check/Physics` check has a `intave/check/physics/` subpackage.<br>

## Dynamic active modules
DA modules are loaded in specific order, are constrained to the `intave/modules/` package,
and have to extend the `/intave/modules/Module` class.<br>

They are cast into groups which classify their purpose.<br>
For example, `intave/module/dispatch/` contains all dispatch modules, `intave/module/tracker/` all tracker modules.<br>

## Static passive modules
Static modules are modules that have a reserved package name and are always loaded.<br>
Think of them as libraries providing a service that are always available.<br>

Examples of static modules are:
- `intave/block/*/`
- `intave/klass/*/`
- `intave/resource/`
- `intave/packet/*/`

Usually, they have one endpoint class that is used to access the module.<br>
For example, the `intave/resource/Resources` class is used to access all resources.<br>

## Services of high order
High order services are old modules that are not yet converted to the new module system.<br>
They are annotated with `@HighOrderService` and ar edirectly loaded by the main IntavePlugin class.<br>
