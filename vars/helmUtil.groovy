def call(body) {
    def dockerRegistry = (env.DOCKER_REGISTRY) ?: '/lemtron';
    def dockerOcImage = (env.DOCKER_OC_IMAGE) ?: '/lemtron/icaas-tools:4';

    withDockerRegistry(registry: [url: dockerRegistry, credentialsId: 'jenkins-tooluser']) {
        sh("docker pull ${dockerOcImage}");
    }

    withDockerContainer(image: dockerOcImage, args:
            """-v "${WORKSPACE}/known-hosts:/home/maven/.ssh/known_hosts"
		   -v /var/run/docker.sock:/var/run/docker.sock
		   --group-add ${env.DOCKER_GID}""") {
        body();
    }
}

def login(String icaasUrl, String credentialsId) {
    withCredentials([usernamePassword(credentialsId: credentialsId,
                                      usernameVariable: 'USERNAME',
                                      passwordVariable: 'PASSWORD')]) {
        sh "oc login --insecure-skip-tls-verify -u$USERNAME -p$PASSWORD $icaasUrl"
    }
}

def validateTemplates(String targetProject) {
    def proj = (targetProject && targetProject != 'off') ? "$targetProject" : 'qa';
    logger.info("validate helm templates and values for: $proj");
    def projectFile = readYaml(file: "helm/project.yaml");

    if (projectFile[proj]) {
        def host = projectFile[proj].host;
        def namespace = projectFile[proj].namespace;
        def chart = projectFile[proj].chart;
        def valueFiles = projectFile[proj].values;

        helmUtil {
            // Validate syntax and values
            def values = valueFiles.collect { "-f $it" }.join(" ");
            sh "helm lint --namespace $namespace $values target/helm/$chart"
        }
    } else {
        logger.error("Require $proj project definition in helm/project.yaml file!");
    }
}

def upgradeChart(String targetProject) {
    def proj = (targetProject && targetProject != 'off' && targetProject != "null") ? "$targetProject" : 'qa';
    logger.info("Upgrade helm chart for: $proj");
    def projectFile = readYaml(file: "helm/project.yaml");

    if (projectFile[proj]) {
        def host = projectFile[proj].host;
        def namespace = projectFile[proj].namespace;
        def chart = projectFile[proj].chart;
        def releaseName = projectFile[proj].releaseName;
        def valueFiles = projectFile[proj].values;
        env.TILLER_NAMESPACE = "$namespace"; //Must be set for helm client

        helmUtil {
            login(host, 'jenkins-tooluser');
            sh "oc project $namespace";

            //Edge Case for Projects using subfolders equal to ./target/helm/app/Chart.yaml
            //Value of chart = ./app/service-name would be different for Release Name and Path of the Chart.yaml
            releaseName = (releaseName != null && releaseName != '')? releaseName : chart;

            // Validate syntax and values
            def values = valueFiles.collect { "-f $it" }.join(" ");
            sh "helm upgrade --install --wait --timeout ${env.HELM_UPGRADE_TIMEOUT} --namespace $namespace $values $releaseName target/helm/$chart"
        }
    } else {
        logger.error("Require $proj project definition in helm/project.yaml file!");
    }
}

def upgradeChart(String cluster, String artifact, String environment, String version) {
    logger.info("Upgrade helm chart cluster:$cluster artifact:$artifact environment:$environment version:$version");

    dir(artifact) {
        helmUtil {
            login(cluster, 'jenkins-tooluser');
            sh "IMAGE_TAG=$version helmfile -e $environment --log-level DEBUG sync"
        }
    }
}

def copyChart(String sourceFolder, String stage = "latest") {
    logger.info("Checkout helm repository");
    sh "rm -rf helm-charts"
    dir("helm-charts") {
        git url: 'ssh://git@bitbucket.1and1.org/mocmd/helm-charts.git', credentialsId: 'jenkins-ssh'

        cryptUtil {
            sh "git-crypt unlock /git-crypt.key"

            def files = sh(script: "find $sourceFolder/* -maxdepth 1 -prune -type d -exec basename {} \\;", returnStdout: true).trim().toLowerCase();
            logger.info("files: $files")
            if (files) {
                files.tokenize('\n').each {
                    sh "rm -rf ./$stage/$it"
                    sh "mkdir -p ./$stage/$it"
                    sh "cp -rf $sourceFolder/$it ./$stage"
                }
            }

            sshagent(['jenkins-ssh']) {
                sh "git add $stage/*"
                sh "git-crypt status"
                sh "git commit -am 'New chart version' | true"
                sh "git push origin master"
            }
        }
    }
}
/*
 * 	// https://github.com/bufferapp/k8s-jenkins-pipeline/blob/master/src/com/buffer/Pipeline3.groovy
 // https://github.com/camptocamp/jenkins-lib-helm/blob/master/src/com/camptocamp/Helm.groovy
 // helm lint
 // helm upgrade --install --wait --timeout 240 --namespace moc-maildevelopment gsuite gsuite/ --set image.tag= 
 // helm repo index . # create or update the index.yaml for repo
 pipeline {
 agent any
 environment {
 ICAAS_URL = "https://openshift-cluster.qacaas.bs.kae.de.iplatform.1and1.org:8443"
 ICAAS_PROJECT = "moc-maildevelopment"
 ARTIFACT_ID = "mhp-hex2019-service"
 }
 stages{
 stage("apply to openshift") {
 steps {
 script {
 logger.banner(STAGE_NAME)
 dir("helm-charts"){
 git url: 'ssh://git@bitbucket.1and1.org/mocmd/helm-charts.git', credentialsId: 'jenkins-ssh'
 }
 icaasUtil{
 icaasUtil.login(env.ICAAS_URL, 'jenkins-tooluser');
 sh "oc project $env.ICAAS_PROJECT";
 sh "helm lint --namespace $env.ICAAS_PROJECT target/helm/$ARTIFACT_ID"
 // Validate syntax and values
 def valueFiles = findFiles(glob: "helm/values/*.y*ml");
 valueFiles.each {
 sh "helm lint --namespace $env.ICAAS_PROJECT -f $it target/helm/$ARTIFACT_ID"
 }
 sh "helm package target/helm/$ARTIFACT_ID || exit 0"
 sh "mv -f $ARTIFACT_ID-* helm-charts/snapshots"
 sh "cd helm-charts"
 sh "helm repo index ."
 }
 dir("helm-charts"){
 mavenUtil{
 sshagent(['jenkins-ssh']) {
 sh "git commit -am 'New chart version'"
 sh "git status"
 sh "git push origin HEAD:master"
 }
 }
 }
 }
 }
 }
 }
 }
 */
