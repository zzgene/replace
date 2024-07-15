/*
 * Copyright 2022 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.project.DefaultProject
import wings.addLocalMaven
import wings.blue
import wings.collectLocalMaven
import wings.getPublishTask
import wings.ignoreReplace
import wings.implementationToCompileOnly
import wings.isAndroidApplication
import wings.isRootProject
import wings.localMaven
import wings.projectToModuleInDependency
import wings.publishAar
import wings.red
import wings.replaceRootTask
import wings.toLocalRepoDirectory

abstract class ReplaceExtension {
    val srcProject: MutableList<String> = mutableListOf()
    fun srcProject(vararg name: String) {
        srcProject.addAll(name)
    }
}

/**
 * This [Settings] plugin is applied to the settings script.
 * The settings script contains the list of modules applied to the project
 * which allows us to hook up on sub-project's creation.
 */
class ReplaceSettings : Plugin<Settings> {

    override fun apply(settings: Settings) {
        val replaceExtension = settings.extensions.create("replace", ReplaceExtension::class.java)
        projectEvaluationListener(settings, replaceExtension)
    }

    private fun projectEvaluationListener(settings: Settings, replaceExtension: ReplaceExtension) {
//        val hasClean = settings.gradle.startParameter.taskRequests.any { it.args.contains("clean") }
//        if (hasClean) {
//            println("startParameter with clean clear LocalMaven all aars ${settings.gradle.rootProject.toLocalRepoDirectory().parentFile.deleteRecursively()}".red)
//        }
        settings.gradle.addProjectEvaluationListener(object : ProjectEvaluationListener {
            override fun beforeEvaluate(project: Project) {

            }

            override fun afterEvaluate(project: Project, state: ProjectState) {
                //是否是源码依赖项目
                val identityPath = (project as DefaultProject).identityPath.toString()
                val isSrcProject = replaceExtension.srcProject.contains(identityPath)
                println("afterEvaluate -> ${replaceExtension.srcProject} ")
                println("afterEvaluate -> project: 【${project.name}】isSrcProject: $isSrcProject")
                //源码依赖项目或者app项目优先处理，因为可能出现切换其他已经发布的模块到源码依赖
                if (isSrcProject || project.isAndroidApplication()) {
                    project.addLocalMaven()
                    project.configurations.all {
                        //源码依赖的project才需要
                        //找到所有本地project依赖，根据需要替换为远端aar依赖
                        projectToModuleInDependency(project)
                    }
                    project.repositories.forEach {
                        println("afterEvaluate repositories >${project.name} ${it.name}")
                    }
                    return
                }
                if (project.ignoreReplace()) {
                    println("afterEvaluate -> project: 【${project.name}】ignore".blue)
                    if (project.isRootProject()) {
                        replaceRootTask(project)
                        localMaven = project.collectLocalMaven()
                        println(
                            "【${project.name}】localMaven size:${localMaven.size} ${
                                localMaven.map { it }.joinToString("\n", "\n") {
                                    val projectName = "【${it.key}】"
                                    "${projectName.padEnd(13, '-')}-> ${it.value}"
                                }
                            }".blue
                        )
                    }
                    return
                }
                //https://docs.gradle.org/current/userguide/declaring_dependencies.html

                //不是源码依赖, 那么需要配置任务发布aar
                project.configurations.all {
                    //非源码依赖project会需要publish成aar依赖，需要把implementation依赖的本地project切换为compileOnly依赖
                    //这样publish成aar的时候就不会把依赖的本地project添加到pom依赖中
                    implementationToCompileOnly(project)
//                        projectToModuleInDependency(project)
                }
                //添加【publish】任务发布aar
                project.publishAar()
                //如果项目配置了publish，那么他依赖的mudle会变成本可以仓库依赖
                //原来Replace-main.basic:helper:unspecified这个是通过project("base:helper")依赖的)
                //当helper配置了publish
                //依赖helper的父级项目中对helper的依赖由project("base:helper")=>aar:helper:dev

                //配置发布aar任务先于preBuild
                val publishTask = project.getPublishTask()
                val firstBuildTask =
                    project.tasks.findByName("preBuild") ?: project.tasks.findByName("compileKotlin") ?: project.tasks.getByName("compileJava")
                println("${project.name} firstBuildTask -> $firstBuildTask")
                firstBuildTask.finalizedBy(publishTask)
            }
        })
    }
}