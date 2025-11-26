package com.company.jenkins

/**
 * Test Utilities
 * 
 * Handles test execution and management
 */
class TestUtils implements Serializable {
    
    private def script
    
    TestUtils(script) {
        this.script = script
    }
    
    /**
     * Execute KVM smoke tests
     */
    def executeKVMSmokeTests(Map config) {
        script.node('baxter') {
            script.timeout(time: config.timeout ?: 240, unit: 'MINUTES') {
                try {
                    script.deleteDir()
                    
                    script.stage('Test: KVM Smoke - Checkout') {
                        script.checkout([
                            $class: 'GitSCM',
                            branches: [[name: "*/${config.gitBranch}"]],
                            userRemoteConfigs: [
                                [credentialsId: config.gitCredentials, url: config.gitUrl]
                            ]
                        ])
                    }
                    
                    script.stage('Test: KVM Smoke - Environment') {
                        script.dir('baxter_taf') {
                            script.writeFile file: 'prep_local_env.json', text: '''
{
    "id": "jenkins_job_sm_test",
    "infrastructure_location": "UK",
    "framework": {
        "defaults": {
            "default_va_platform": "kvm"
        },
        "selenium_server": {
            "connection_type": "grid"
        },
        "iph_server": {
            "target_host": "10.62.149.127"
        },
        "dev_options": {
            "deployment": {
                "async": true
            },
            "initial_setup": {
                "headless_setup": true
            }
        }
    },
    "builds": {
        "sd": {
            "kvm": "<BUILD_SERV>/Services_Director/master/latest/kvm_image.qcow2"
        },
        "vtm": {
            "preset_id": "vtm_19.1"
        }
    }
}'''
                        }
                    }
                    
                    script.stage('Test: KVM Smoke - Execute') {
                        script.dir('baxter_taf') {
                            script.sh '''
                                ./prep_local_env.sh
                                . framework/environment/virtual_env/bin/activate
                                cd tests
                                nosetests -s --exe -a prod=sd,type=smoke --with-xunit
                            '''
                        }
                    }
                    
                    this.publishTestResults('KVM Smoke')
                    
                } catch (Exception e) {
                    script.currentBuild.result = 'FAILURE'
                    script.echo "❌ KVM Smoke tests failed: ${e.getMessage()}"
                    throw e
                } finally {
                    this.archiveTestArtifacts()
                    script.deleteDir()
                }
            }
        }
    }
    
    /**
     * Execute general smoke tests
     */
    def executeSmokeTests(Map config) {
        script.node('baxter') {
            script.timeout(time: config.timeout ?: 240, unit: 'MINUTES') {
                try {
                    script.deleteDir()
                    
                    script.stage('Test: Smoke - Checkout') {
                        script.checkout([
                            $class: 'GitSCM',
                            branches: [[name: "*/${config.gitBranch}"]],
                            userRemoteConfigs: [
                                [credentialsId: config.gitCredentials, url: config.gitUrl]
                            ]
                        ])
                    }
                    
                    script.stage('Test: Smoke - Environment') {
                        this.createTestEnvironment([
                            platform: 'kvm',
                            buildServ: '<BUILD_SERV>/Services_Director/master/latest/kvm_image.qcow2',
                            vtmPreset: 'vtm_19.1'
                        ])
                    }
                    
                    script.stage('Test: Smoke - Execute') {
                        this.runNoseTests([
                            testFilter: 'prod=sd,type=smoke'
                        ])
                    }
                    
                    this.publishTestResults('Smoke')
                    
                } catch (Exception e) {
                    script.currentBuild.result = 'FAILURE'
                    script.echo "❌ Smoke tests failed: ${e.getMessage()}"
                    throw e
                } finally {
                    this.archiveTestArtifacts()
                    script.deleteDir()
                }
            }
        }
    }
    
    /**
     * Execute analytics tests
     */
    def executeAnalyticsTests(Map config) {
        script.node('baxter') {
            script.deleteDir()
            
            script.stage('Test: Analytics - Checkout') {
                script.checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${config.gitBranch}"]],
                    userRemoteConfigs: [
                        [credentialsId: config.gitCredentials, url: config.gitUrl]
                    ]
                ])
            }
            
            script.stage('Test: Analytics - Environment') {
                this.createTestEnvironment([
                    platform: 'esx',
                    buildServ: '<BUILD_SERV>/Services_Director/Releases/20.1/vmware_image.ova',
                    vtmPreset: 'vtm_20.1'
                ])
            }
            
            script.stage('Test: Analytics - Execute') {
                this.runNoseTests([
                    testFilter: 'prod=sd,type=analytics_smoke,func=analytics_app'
                ])
            }
            
            this.publishTestResults('Analytics')
            this.archiveTestArtifacts()
        }
    }
    
    /**
     * Execute upgrade tests
     */
    def executeUpgradeTests(Map config) {
        script.node('baxter') {
            script.deleteDir()
            script.timeout(time: 240, unit: 'MINUTES') {
                try {
                    script.stage('Test: Upgrade - Checkout') {
                        script.checkout([
                            $class: 'GitSCM',
                            branches: [[name: "*/${config.gitBranch}"]],
                            userRemoteConfigs: [
                                [credentialsId: config.gitCredentials, url: config.gitUrl]
                            ]
                        ])
                    }
                    
                    script.stage('Test: Upgrade - Environment') {
                        script.dir('baxter_taf') {
                            script.writeFile file: 'prep_local_env.json', text: '''
{
    "id": "jenkins_job_sm_test",
    "infrastructure_location": "UK",
    "framework": {
        "defaults": {
            "default_va_platform": "kvm"
        },
        "selenium_server": {
            "connection_type": "grid"
        },
        "iph_server": {
            "target_host": "10.62.149.127"
        },
        "dev_options": {
            "deployment": {
                "async": true
            },
            "initial_setup": {
                "headless_setup": true
            }
        }
    },
    "builds": {
        "sd": {
            "esx": "<BUILD_SERV>/Services_Director/release-21.1r2/latest/vmware_image.ova",
            "product_version": "21.1",
            "upgrade": "http://uk-sd-builds.cam.zeus.com/Services_Director/release-21.1r2/latest/vmware_image.img"
        },
        "vtm": {
            "preset_id": "vtm_19.1"
        }
    }
}'''
                        }
                    }
                    
                    script.stage('Test: Upgrade - Execute') {
                        this.runNoseTests([
                            testFilter: 'prod=sd,type=upgrade_smoke'
                        ])
                    }
                    
                    this.publishTestResults('Upgrade')
                    
                } catch (Exception e) {
                    script.currentBuild.result = 'FAILURE'
                    throw e
                } finally {
                    this.archiveTestArtifacts()
                    script.deleteDir()
                }
            }
        }
    }
    
    /**
     * Execute version-specific tests
     */
    def executeVersionTests(Map config) {
        script.node('baxter') {
            script.deleteDir()
            script.timeout(time: 240, unit: 'MINUTES') {
                try {
                    script.stage('Test: Version - Checkout') {
                        script.checkout([
                            $class: 'GitSCM',
                            branches: [[name: "*/${config.gitBranch}"]],
                            userRemoteConfigs: [
                                [credentialsId: config.gitCredentials, url: config.gitUrl]
                            ]
                        ])
                    }
                    
                    script.stage('Test: Version - Environment') {
                        this.createTestEnvironment([
                            platform: 'kvm',
                            buildServ: "<BUILD_SERV>/Services_Director/release-${config.version}/latest/vmware_image.ova",
                            vtmPreset: 'vtm_21.1'
                        ])
                    }
                    
                    script.stage('Test: Version - Execute') {
                        this.runNoseTests([
                            testFilter: "prod=sd,type=smoke,version=${config.version}"
                        ])
                    }
                    
                    this.publishTestResults("Version ${config.version}")
                    
                } catch (Exception e) {
                    script.currentBuild.result = 'FAILURE'
                    throw e
                } finally {
                    this.archiveTestArtifacts()
                    script.deleteDir()
                }
            }
        }
    }
    
    /**
     * Create test environment configuration
     */
    private def createTestEnvironment(Map config) {
        def envConfig = [
            id: "jenkins_job_sm_test",
            infrastructure_location: "UK",
            framework: [
                defaults: [
                    default_va_platform: config.platform
                ],
                selenium_server: [
                    connection_type: "grid"
                ],
                iph_server: [
                    target_host: "10.62.149.127"
                ],
                dev_options: [
                    deployment: [
                        async: true
                    ],
                    initial_setup: [
                        headless_setup: true
                    ]
                ]
            ],
            builds: [
                sd: [:],
                vtm: [
                    preset_id: config.vtmPreset
                ]
            ]
        ]
        
        envConfig.builds.sd[config.platform] = config.buildServ
        
        script.writeFile(
            file: 'baxter_taf/prep_local_env.json',
            text: script.writeJSON(returnText: true, json: envConfig, pretty: 4)
        )
    }
    
    /**
     * Run nose tests
     */
    private def runNoseTests(Map config) {
        script.dir('baxter_taf') {
            script.sh """
                ./prep_local_env.sh
                . framework/environment/virtual_env/bin/activate
                cd tests
                nosetests -s --exe -a ${config.testFilter} --with-xunit
            """
        }
    }
    
    /**
     * Publish test results
     */
    private def publishTestResults(String testType) {
        script.echo "📊 Publishing ${testType} test results"
        try {
            script.junit allowEmptyResults: true, testResults: '**/nosetests.xml'
            this.analyzeTestResults(testType)
        } catch (Exception e) {
            script.echo "⚠️ Failed to publish test results: ${e.getMessage()}"
        }
    }
    
    /**
     * Archive test artifacts
     */
    private def archiveTestArtifacts() {
        try {
            script.archiveArtifacts(
                artifacts: '**/baxter_taf/tests/artifacts/logs/**',
                allowEmptyArchive: true
            )
        } catch (Exception e) {
            script.echo "⚠️ Failed to archive test artifacts: ${e.getMessage()}"
        }
    }
    
    /**
     * Analyze test results and set build status
     */
    private def analyzeTestResults(String testType) {
        try {
            def testResults = script.currentBuild.rawBuild.getAction(hudson.tasks.junit.TestResultAction)
            
            if (testResults == null) {
                script.echo "📊 ${testType}: No test data found. Marking as aborted."
                script.currentBuild.result = 'ABORTED'
                return
            }
            
            def passCount = testResults.getPassedTestsCount()
            def failCount = testResults.getFailedTestsCount()
            def skipCount = testResults.getSkippedTestsCount()
            def totalCount = passCount + failCount + skipCount
            
            script.echo "📊 ${testType} Results: Passed ${passCount}, Failed ${failCount}, Skipped ${skipCount}, Total ${totalCount}"
            
            if ((skipCount > passCount) && (skipCount > failCount)) {
                script.echo "⚠️ ${testType}: More tests skipped than passed. Marking as unstable."
                script.currentBuild.result = 'UNSTABLE'
            } else if (skipCount > 0) {
                script.echo "⚠️ ${testType}: Some tests skipped, please investigate."
                script.currentBuild.result = 'UNSTABLE'
            }
            
        } catch (Exception e) {
            script.echo "⚠️ Failed to analyze test results: ${e.getMessage()}"
        }
    }
}