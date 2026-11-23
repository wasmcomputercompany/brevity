Brevity Interop Testing
=======================

This project generates sample Rust and Kotlin projects, executes them, and confirms they behave the
same.

Structure
---------

This is implemented by `BrevityExecutionTester`, which generates projects structured like this:

| Path                                     | Notes            |
|:-----------------------------------------|:-----------------|
| `project.yaml`                           | Kotlin Toolchain |
| `api`                                    |                  |
| ` '- module.yaml`                        | Kotlin Toochain  |
| ` '- src`                                |                  |
| `   '- wit`                              |                  |
| `     '- wasmo`                          |                  |
| `       '- testing`                      |                  |
| `         '- BridgeTypeTest.kt`          | Test data        |
| `guest`                                  |                  |
| ` '- module.yaml`                        | Kotlin Toochain  |
| `   '- src`                              |                  |
| `     '- wit`                            |                  |
| `       '- wasmo`                        |                  |
| `         '- testing`                    |                  |
| `           '- BridgeTypeTestGuest.kt`   | Brevity output   |
| `     '- dev`                            |                  |
| `       '- wasmo`                        |                  |
| `         '- brevity`                    |                  |
| `           '- integration`              |                  |
| `             '- GuestImplementation.kt` | Test data        |
| `host`                                   |                  |
| ` '- module.yaml`                        | Kotlin Toochain  |
| ` '- src`                                |                  |
| `    '- wit`                             |                  |
| `      '- wasmo`                         |                  |
| `        '- testing`                     |                  |
| `          '- BridgeTypeTestHost.kt`     | Brevity output   |
| `    '- dev`                             |                  |
| `      '- wasmo`                         |                  |
| `        '- brevity`                     |                  |
| `          '- integration`               |                  |
| `            '- HostMain.kt`             | Test data        |
| `wit`                                    |                  |
| ` '- bridge-type-test.wit`               | Brevity input    |


Test Repository
---------------

The inputs to these tests include both Brevity's generated code as well as Brevity's runtime
and dependencies. The Kotlin Toolchain resolves libraries from Maven Central or Maven Local
so `jvmTest` publishes Brevity under the marker version `0-testing` and syncs it to
`build/testRepository` which keeps unchanged artifacts timestamps, so incremental builds survive,
and points the toolchain there.
