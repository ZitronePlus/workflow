# Shared pipeline workflow libs for special pipeline flow control

See [Jenkins Shared Libraries](https://jenkins.io/doc/book/pipeline/shared-libraries) for more documentation. The script is added automatically while moc-jenkins setup, see moc-jenkis project, init-scripts 01_global_workflow_libs.groovy

## Best Practices:
1. Implement functionality in Util.groovy scripts and add a method call to the Pipeline definition ```script { util.method()...``` This enables the possibility to test on jenkins via Build replay (#Build Link under the project beneath the console out). Opens a Main Script window where you can add for example:

```
@Library('moc-workflow@development') _
pipeline {
    agent any

	stages{
	    stage("test") {
	            steps {
	                script {
	                   logger.banner(STAGE_NAME)
	                   mavenUtil.buildDocker();
	                }
	            }
	     }   
	}
}
```
Test pipeline code
