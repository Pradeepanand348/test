#!/usr/bin/env groovy

/**
 * AMI Builder Step
 * 
 * Handles AMI building process on ami-builder node
 */
def call(Map config = [:]) {
    def nodeName = config.nodeName ?: 'ami-builder'
    def timeout = config.timeout ?: 60
    
    node(nodeName) {
        timeout(time: timeout, unit: 'MINUTES') {
            try {
                // Set environment variables
                env.GIT_BRANCH = config.gitBranch
                env.GIT_CREDS = config.gitCredentials
                env.GIT_URL1 = config.gitUrl
                env.BUILD_JOB = config.buildJob
                
                stage('AMI: Checkout Repository') {
                    gitUtils.checkoutRepository([
                        branch: env.GIT_BRANCH,
                        credentialsId: env.GIT_CREDS,
                        url: env.GIT_URL1
                    ])
                }
                
                stage('AMI: Prepare Build Environment') {
                    buildUtils.prepareAMIEnvironment([
                        sourceHost: '10.34.132.15',
                        buildPath: '/work/builds/master/latest',
                        localPath: '/home/jenkins_slave_user/workspace/master/scripts'
                    ])
                }
                
                stage('AMI: Execute AMI Build') {
                    dir('scripts') {
                        buildUtils.executeAMIBuild([
                            pemFile: 'sd-ami-build.pem',
                            buildScript: 'ova_to_ami_remote.py',
                            buildNumberFile: 'build-number.txt',
                            imageFile: 'vmware_image.ova'
                        ])
                    }
                }
                
                echo "✅ AMI build completed successfully"
                
            } catch (Exception e) {
                currentBuild.result = 'FAILURE'
                echo "❌ AMI build failed: ${e.getMessage()}"
                throw e
            }
        }
    }
}