Brevity Integration Testing
===========================

This project executes its own generated code to confirm that code behaves as expected.

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


Maven Local
-----------

The inputs to this program include both Brevity's generated code, but also the Brevity runtime
libraries and dependencies. The easiest way to provide these libraries to the Kotlin Toolchain is
via Maven Local (`~/.m2/repository`), published with a special marker version (`0-testing`).

To publish concurrently with the rest of the Brevity build, we fork a Gradle build with a separate
output directory (`build/publish-for-tests`). We'd prefer to not fork a Gradle build, but that would
contaminate Maven Local!
