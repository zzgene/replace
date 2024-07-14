package wings

import org.gradle.api.Project

fun gitUpdateTask(rootProject: Project) {
    rootProject.tasks.register("replaceUpdate") {
        group = "git update"
        doLast {
            //通过git指令判断和远端最新代码相比哪些文件有修改
            val diffFiles = diffWithHead()
            //根据修改的文件判断哪些aar要重新发布，修改所在的模块要删除localMaven中的aar
            //执行git命令更新代码，可能有冲突
            println(diffFiles)
            println("git pull".exec())
        }
    }
}