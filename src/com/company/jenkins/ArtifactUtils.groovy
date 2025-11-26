package com.company.jenkins

/**
 * Artifact Utilities
 * 
 * Handles artifact management operations
 */
class ArtifactUtils implements Serializable {
    
    private def script
    
    ArtifactUtils(script) {
        this.script = script
    }
    
    /**
     * Package artifacts with commit hash
     */
    def packageArtifacts(Map config) {
        script.echo "📦 Packaging artifacts with commit hash: ${config.commitHash}"
        
        config.artifactTypes.each { type ->
            switch(type) {
                case 'test_licenses':
                    script.sh """
                        echo "📄 Packaging test licenses..."
                        tar -zcvf test_licenses-${config.commitHash}.tgz products/scms/mim/test_licenses/*
                    """
                    break
                case 'test_certs':
                    script.sh """
                        echo "🔐 Packaging test certificates..."
                        tar -zcvhf test_certs-${config.commitHash}.tgz products/scms/mim/test_certs/*
                    """
                    break
                case 'linux_scripts':
                    script.sh """
                        echo "🐧 Packaging Linux scripts..."
                        tar -zcvf linux_scripts-${config.commitHash}.tgz products/scms/mim/deployment/scripts/linux/*
                    """
                    break
                default:
                    script.echo "⚠️ Unknown artifact type: ${type}"
            }
        }
        
        script.echo "✅ Artifact packaging completed"
    }
    
    /**
     * Copy artifacts to remote server
     */
    def copyToRemoteServer(Map config) {
        script.echo "📤 Copying artifacts to remote server"
        script.echo "Source: ${config.sourceDir}"
        script.echo "Target: ${config.username}@${config.targetHost}:${config.targetDir}"
        
        script.sh """
            echo "📋 Copying RPM files..."
            rsync -r ${config.sourceDir}/*.rpm ${config.username}@${config.targetHost}:${config.targetDir}/ || true
            
            echo "📋 Copying build variables..."
            rsync -r ${config.sourceDir}/buildVars.txt ${config.username}@${config.targetHost}:${config.targetDir}/ || true
            
            echo "✅ Artifact copy completed"
        """
    }
    
    /**
     * Prepare local artifacts from remote source
     */
    def prepareLocalArtifacts(Map config) {
        script.echo "🔄 Preparing local artifacts from remote source"
        
        script.sh """
            echo "🧹 Cleaning local artifact directory..."
            rm -rf ${config.localDir}
            mkdir -p ${config.localDir}
            
            echo "📥 Copying RPM files..."
            cp ${config.targetDir}/*.rpm ${config.localDir}/
            
            echo "📥 Copying build variables..."
            cp ${config.targetDir}/buildVars.txt ${config.localDir}/
            
            echo "✅ Local artifact preparation completed"
        """
    }
    
    /**
     * Archive artifacts in Jenkins
     */
    def archiveArtifacts(Map config) {
        def artifacts = config.artifacts ?: '**/*'
        def allowEmpty = config.allowEmptyArchive ?: true
        
        script.echo "🗄️ Archiving artifacts: ${artifacts}"
        
        script.archiveArtifacts(
            artifacts: artifacts,
            allowEmptyArchive: allowEmpty,
            fingerprint: config.fingerprint ?: false
        )
    }
    
    /**
     * Clean up old artifacts
     */
    def cleanupOldArtifacts(Map config) {
        def retentionDays = config.retentionDays ?: 30
        def directory = config.directory ?: './artifacts'
        
        script.echo "🧹 Cleaning up artifacts older than ${retentionDays} days in ${directory}"
        
        script.sh """
            find ${directory} -type f -mtime +${retentionDays} -delete || true
            echo "✅ Cleanup completed"
        """
    }
    
    /**
     * Create artifact manifest
     */
    def createManifest(Map config) {
        def manifestContent = [
            timestamp: new Date().toString(),
            commitHash: config.commitHash,
            branch: config.branch,
            buildNumber: script.env.BUILD_NUMBER,
            artifacts: config.artifacts ?: []
        ]
        
        script.writeJSON(
            file: 'artifact-manifest.json',
            json: manifestContent,
            pretty: 4
        )
        
        script.echo "📋 Artifact manifest created"
    }
    
    /**
     * Verify artifact integrity
     */
    def verifyArtifacts(Map config) {
        script.echo "🔍 Verifying artifact integrity"
        
        config.artifacts.each { artifact ->
            script.sh """
                if [ -f "${artifact}" ]; then
                    echo "✅ ${artifact} - Found"
                    ls -lh "${artifact}"
                else
                    echo "❌ ${artifact} - Missing"
                    exit 1
                fi
            """
        }
        
        script.echo "✅ Artifact verification completed"
    }
}