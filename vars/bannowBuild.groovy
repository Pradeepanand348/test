#!/usr/bin/env groovy

/**
 * Bannow Builder Step
 * 
 * Handles the build process on bannow-builder node
 */
def call(Map config = [:]) {
    def nodeName = config.nodeName ?: 'bannow-builder'
    def timeout = config.timeout ?: 45
    
    node(nodeName) {
        timeout(time: timeout, unit: 'MINUTES') {
            try {
                // Set environment variables
                env.GIT_BRANCH = config.gitBranch
                env.GIT_CREDS = config.gitCredentials
                env.GIT_URL1 = config.gitUrl
                env.GIT_BROWSER_URL = config.gitBrowserUrl ?: "https://bitbucket.cam.zeus.com/projects/SD/repos/sd-build"
                
                stage('Bannow: Checkout Repository') {
                    gitUtils.checkoutRepository([
                        branch: env.GIT_BRANCH,
                        credentialsId: env.GIT_CREDS,
                        url: env.GIT_URL1,
                        browser: [
                            $class: 'Stash',
                            repoUrl: env.GIT_BROWSER_URL
                        ]
                    ])
                    echo "🌐 GIT_BROWSER_URL is set to: ${env.GIT_BROWSER_URL}"
                }
                
                stage('Bannow: Prepare Artifacts') {
                    artifactUtils.prepareLocalArtifacts([
                        sourceHost: '10.34.132.15',
                        sourceDir: '/work/artifacts/sd-repo',
                        localDir: 'sd-repo',
                        targetDir: '/work/jenkins/jenkins_root/workspace/master/sd-repo'
                    ])
                }
                
                stage('Bannow: Execute Build') {
                    buildUtils.executeBannowBuild([
                        logLevel: config.logLevel ?: 'DEBUG',
                        publishHosts: config.publishHosts ?: 'us-sd-builds.englab.brocade.com',
                        buildsToKeep: config.buildsToKeep ?: 3,
                        buildQcow2: config.buildQcow2 ?: 'yes',
                        sdRpms: "${env.WORKSPACE}/sd-repo"
                    ])
                }
                
                echo "✅ Bannow build completed successfully"
                
            } catch (Exception e) {
                currentBuild.result = 'FAILURE'
                echo "❌ Bannow build failed: ${e.getMessage()}"
                throw e
            }
        }
    }
}