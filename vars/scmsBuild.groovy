#!/usr/bin/env groovy

/**
 * SCMS Core Build Step
 * 
 * Builds the SCMS core components on CentOS VA node
 */
def call(Map config = [:]) {
    def nodeName = config.nodeName ?: 'scms_build_centos_va'
    def timeout = config.timeout ?: 60
    
    try {
        node(nodeName) {
            timeout(time: timeout, unit: 'MINUTES') {
                // Set environment variables
                env.GIT_BRANCH = config.gitBranch
                env.GIT_CREDS = config.gitCredentials
                env.GIT_URL = config.gitUrl
                env.GIT_BROWSER_URL = config.gitBrowserUrl ?: "https://bitbucket.cam.zeus.com/projects/SD/repos/sd-build"
                
                def buildConfig = [
                    NOCACHE: config.noCache ?: 1,
                    MASTERWKSPACE: "${env.ZWORKSPACE}/var/lib/jenkins/jobs/${env.JOB_NAME}/workspace/products",
                    SLAVEWKSPACE: "${env.ZWORKSPACE}/jenkins_slave_user/localhome/jenkins_slave_user/workspace/${env.JOB_NAME}/products"
                ]
                
                stage('SCMS: Checkout Repository') {
                    gitUtils.checkoutRepository([
                        branch: env.GIT_BRANCH,
                        credentialsId: env.GIT_CREDS,
                        url: env.GIT_URL
                    ])
                }
                
                stage('SCMS: Get Commit Info') {
                    env.GIT_COMMIT_SHORT = gitUtils.getShortCommitHash()
                    echo "📝 Commit short hash: ${env.GIT_COMMIT_SHORT}"
                }
                
                stage('SCMS: Core Build') {
                    buildUtils.executeSCMSBuild(buildConfig)
                }
                
                stage('SCMS: Package Artifacts') {
                    artifactUtils.packageArtifacts([
                        commitHash: env.GIT_COMMIT_SHORT,
                        artifactTypes: ['test_licenses', 'test_certs', 'linux_scripts']
                    ])
                }
                
                stage('SCMS: Copy Artifacts') {
                    artifactUtils.copyToRemoteServer([
                        sourceDir: '/home/jenkins_slave_user/space/artifacts',
                        targetHost: '10.34.132.15',
                        targetDir: '/work/artifacts/sd-repo',
                        username: 'jenkins'
                    ])
                }
                
                echo "✅ SCMS build completed successfully. Proceeding with subsequent builds."
            }
        }
    } catch (Exception e) {
        currentBuild.result = 'FAILURE'
        echo "❌ SCMS build failed: ${e.getMessage()}"
        
        notificationUtils.sendFailureNotification([
            stage: 'SCMS Core Build',
            error: e.getMessage(),
            jobName: env.JOB_NAME,
            buildNumber: env.BUILD_NUMBER,
            buildUrl: env.BUILD_URL,
            gitBranch: env.GIT_BRANCH,
            gitCommit: env.GIT_COMMIT_SHORT,
            recipients: config.notificationEmail
        ])
        
        error("Pipeline stopped due to SCMS build failure: ${e.getMessage()}")
    }
}