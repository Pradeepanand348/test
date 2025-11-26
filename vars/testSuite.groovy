#!/usr/bin/env groovy

/**
 * Test Suite Orchestrator
 * 
 * Manages all test execution phases
 */
def call(Map config = [:]) {
    def nodeName = config.nodeName ?: 'baxter'
    def timeout = config.timeout ?: 240
    def testTypes = config.testTypes ?: ['smoke', 'analytics', 'upgrade']
    
    node(nodeName) {
        try {
            // Set environment variables
            env.GIT_BRANCH = config.gitBranch
            env.GIT_CREDS = config.gitCredentials
            env.GIT_URL2 = config.gitUrl
            
            stage('Test: Build Retrieval') {
                buildUtils.retrieveBuilds([
                    targetHost: '10.34.132.26',
                    serverHost: '10.34.132.15',
                    branch: 'master'
                ])
            }
            
            stage('Test: Parallel Execution') {
                def parallelTests = [:]
                
                if ('smoke' in testTypes) {
                    parallelTests['KVM Smoke Tests'] = {
                        testUtils.executeKVMSmokeTests([
                            gitBranch: env.GIT_BRANCH,
                            gitCredentials: env.GIT_CREDS,
                            gitUrl: env.GIT_URL2,
                            timeout: 240
                        ])
                    }
                    
                    parallelTests['General Smoke Tests'] = {
                        testUtils.executeSmokeTests([
                            gitBranch: env.GIT_BRANCH,
                            gitCredentials: env.GIT_CREDS,
                            gitUrl: env.GIT_URL2,
                            timeout: 240
                        ])
                    }
                }
                
                if ('analytics' in testTypes) {
                    parallelTests['Analytics Tests'] = {
                        testUtils.executeAnalyticsTests([
                            gitBranch: env.GIT_BRANCH,
                            gitCredentials: env.GIT_CREDS,
                            gitUrl: env.GIT_URL2
                        ])
                    }
                }
                
                if ('upgrade' in testTypes) {
                    parallelTests['Upgrade Tests'] = {
                        testUtils.executeUpgradeTests([
                            gitBranch: env.GIT_BRANCH,
                            gitCredentials: env.GIT_CREDS,
                            gitUrl: env.GIT_URL2
                        ])
                    }
                }
                
                parallelTests['Version Tests'] = {
                    testUtils.executeVersionTests([
                        gitBranch: env.GIT_BRANCH,
                        gitCredentials: env.GIT_CREDS,
                        gitUrl: env.GIT_URL2,
                        version: '21.1r1'
                    ])
                }
                
                // Execute all tests in parallel
                parallel(parallelTests)
            }
            
            echo "✅ All test suites completed successfully"
            
        } catch (Exception e) {
            currentBuild.result = 'FAILURE'
            echo "❌ Test suite failed: ${e.getMessage()}"
            throw e
        } finally {
            // Archive test results and logs
            publishTestResults(
                testResultsPattern: '**/nosetests.xml',
                allowEmptyResults: true
            )
            archiveArtifacts(
                artifacts: '**/baxter_taf/tests/artifacts/logs/**',
                allowEmptyArchive: true
            )
        }
    }
}