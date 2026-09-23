package dev.wasmo.brevity.gradle

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
internal abstract class PublishWitTask : DefaultTask() {
  init {
    group = "brevity"
    description = "Package and publish wit interface(s)"
  }

  @get:Inject
  abstract val execOperations: ExecOperations

  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputWkgConfig: RegularFileProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputWkgWorkingDir: DirectoryProperty

  @get:Input
  @get:Optional
  abstract val inputIsWorkspace: Property<Boolean>

  @TaskAction
  fun execute() {
    execOperations.exec {
      WkgRunner(inputWkgConfig, inputWkgWorkingDir)
        .exec(this, "publish") {
          if (inputIsWorkspace.getOrElse(false)) {
            add("--workspace")
          }
        }
    }
  }
}
