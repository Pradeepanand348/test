#!/usr/bin/env groovy

/**
 * Main SD Build Pipeline
 * 
 * This is the main entry point for the SD Build pipeline.
 * It orchestrates the entire build process across multiple nodes.
 * 
 * @param config Map containing pipeline configuration
 */
def call(Map config = [:]) {
    pipeline {
        agent none
        
        parameters {
            choice(
                name: 'GIT_BRANCH',
                choices: ['master', 'develop', 'release/*'],
                description: 'Git branch to build'
            )
            string(
                name: 'GIT_CREDENTIALS',
                defaultValue: 'git-credentials',
                description: 'Git credentials ID'
            )
            booleanParam(
                name: 'SKIP_TESTS',
                defaultValue: false,
                description: 'Skip test execution'
            )
        }
        
        environment {
            BUILD_TIMESTAMP = sh(script: 'date +%Y%m%d_%H%M%S', returnStdout: true).trim()
            NOTIFICATION_EMAIL = config.notificationEmail ?: 'pradeep.anand@ivanti.com'
        }
        
        stages {
            stage('Initialize') {
                steps {
                    script {
                        echo "🚀 Starting SD Build Pipeline"
                        echo "Branch: ${params.GIT_BRANCH}"
                        echo "Build Timestamp: ${env.BUILD_TIMESTAMP}"
                        
                        // Initialize build configuration
                        env.BUILD_CONFIG = buildUtils.initializeBuildConfig(config)
                    }
                }
            }
            
            stage('SCMS Core Build') {
                steps {
                    script {
                        scmsBuild {
                            gitBranch = params.GIT_BRANCH
                            gitCredentials = params.GIT_CREDENTIALS
                            gitUrl = config.gitUrl
                            notificationEmail = env.NOTIFICATION_EMAIL
                        }
                    }
                }
            }
            
            stage('Parallel Builds') {
                parallel {
                    stage('Bannow Builder') {
                        steps {
                            script {
                                bannowBuild {
                                    gitBranch = params.GIT_BRANCH
                                    gitCredentials = params.GIT_CREDENTIALS
                                    gitUrl = config.gitUrl1
                                    dependsOn = 'scms-core-build'
                                }
                            }
                        }
                    }
                    
                    stage('AMI Builder') {
                        steps {
                            script {
                                amiBuild {
                                    gitBranch = params.GIT_BRANCH
                                    gitCredentials = params.GIT_CREDENTIALS
                                    gitUrl = config.gitUrl1
                                    buildJob = config.buildJob
                                }
                            }
                        }
                    }
                    
                    stage('Test Suite') {
                        when {
                            not { params.SKIP_TESTS }
                        }
                        steps {
                            script {
                                testSuite {
                                    gitBranch = params.GIT_BRANCH
                                    gitCredentials = params.GIT_CREDENTIALS
                                    gitUrl = config.gitUrl2
                                    testTypes = ['smoke', 'analytics', 'upgrade']
                                }
                            }
                        }
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    notificationUtils.sendBuildNotification([
                        result: currentBuild.result,
                        jobName: env.JOB_NAME,
                        buildNumber: env.BUILD_NUMBER,
                        buildUrl: env.BUILD_URL,
                        gitBranch: params.GIT_BRANCH,
                        buildTimestamp: env.BUILD_TIMESTAMP,
                        recipients: env.NOTIFICATION_EMAIL
                    ])
                }
            }
            success {
                script {
                    echo "✅ Pipeline completed successfully!"
                    archiveArtifacts artifacts: '**/build-artifacts/**', allowEmptyArchive: true
                }
            }
            failure {
                script {
                    echo "❌ Pipeline failed!"
                    notificationUtils.sendFailureNotification([
                        jobName: env.JOB_NAME,
                        buildNumber: env.BUILD_NUMBER,
                        buildUrl: env.BUILD_URL,
                        recipients: env.NOTIFICATION_EMAIL
                    ])
                }
            }
        }
    }
}