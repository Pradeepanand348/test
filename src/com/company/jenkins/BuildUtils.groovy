package com.company.jenkins

/**
 * Build Utilities
 * 
 * Handles build-related operations
 */
class BuildUtils implements Serializable {
    
    private def script
    
    BuildUtils(script) {
        this.script = script
    }
    
    /**
     * Initialize build configuration
     */
    def initializeBuildConfig(Map config) {
        def defaultConfig = [
            timeout: 60,
            retries: 3,
            parallel: true,
            notifications: true,
            cleanup: true
        ]
        
        return defaultConfig + config
    }
    
    /**
     * Execute SCMS build process
     */
    def executeSCMSBuild(Map config) {
        script.echo "🔨 Starting SCMS build process"
        
        script.sh """
            export NOCACHE=${config.NOCACHE}
            export MASTERWKSPACE=${config.MASTERWKSPACE}
            export SLAVEWKSPACE=${config.SLAVEWKSPACE}
            
            echo "🧹 Cleaning workspaces..."
            echo "No cache: \$NOCACHE"
            echo "Master workspace: \$MASTERWKSPACE"
            echo "Slave workspace: \$SLAVEWKSPACE"
            
            # Clean existing workspaces
            if [ -d "\$MASTERWKSPACE" ]; then 
                rm -rf "\$MASTERWKSPACE"
            fi
            if [ -d "\$SLAVEWKSPACE" ]; then 
                rm -rf "\$SLAVEWKSPACE"
            fi
            
            echo "⚙️ Running configuration tool..."
            ./conftool
            
            echo "📁 Changing to MIM directory..."
            cd products/scms/mim
            
            echo "🏗️ Setting build environment..."
            export VABUILD=Y
            
            # Uncomment these when ready for actual build
            #make build_tgz build_qa_tgz rpm
            #echo "P4_CHANGELIST=${script.env.GIT_COMMIT_SHORT}" > buildVars.txt
            
            echo "✅ SCMS build process completed"
        """
    }
    
    /**
     * Execute Bannow build process
     */
    def executeBannowBuild(Map config) {
        script.echo "🔨 Starting Bannow build process"
        
        script.sh """
            export LOG_LEVEL=${config.logLevel}
            export PUBLISH_HOSTS=${config.publishHosts}
            export BUILDS_TO_KEEP=${config.buildsToKeep}
            export BUILD_QCOW2=${config.buildQcow2}
            export SD_RPMS=${config.sdRpms}
            
            echo "📊 Build Configuration:"
            echo "Log Level: \$LOG_LEVEL"
            echo "Publish Hosts: \$PUBLISH_HOSTS"
            echo "Builds to Keep: \$BUILDS_TO_KEEP"
            echo "Build QCOW2: \$BUILD_QCOW2"
            echo "SD RPMs: \$SD_RPMS"
            
            # Uncomment when ready for actual build
            # ./run-build.sh
            
            echo "✅ Bannow build process completed"
        """
    }
    
    /**
     * Prepare AMI build environment
     */
    def prepareAMIEnvironment(Map config) {
        script.echo "🔧 Preparing AMI build environment"
        
        script.sh """
            echo "📥 Syncing build artifacts from ${config.sourceHost}..."
            rsync -avz jenkins@${config.sourceHost}:${config.buildPath}/build-number.txt ${config.localPath}/
            rsync -avz jenkins@${config.sourceHost}:${config.buildPath}/deliverables/bin/image_vmware/image.ova ${config.localPath}/
            
            echo "🔄 Renaming image file..."
            mv image.ova vmware_image.ova
            
            echo "✅ AMI environment prepared"
        """
    }
    
    /**
     * Execute AMI build process
     */
    def executeAMIBuild(Map config) {
        script.echo "🏗️ Starting AMI build process"
        
        script.sh """
            echo "🔐 Setting permissions for PEM file..."
            chmod 700 ${config.pemFile}
            
            echo "🐍 Running AMI build script..."
            python ${config.buildScript} --build=\$(cat ${config.buildNumberFile}) ${config.imageFile}
            
            # Alternative command for live builds:
            #python ${config.buildScript} --live --build=20.1 ${config.imageFile}
            
            echo "✅ AMI build completed"
        """
    }
    
    /**
     * Retrieve builds from remote server
     */
    def retrieveBuilds(Map config) {
        script.echo "📥 Retrieving builds from remote server"
        
        script.sh """
            ssh -T root@${config.targetHost} <<EOF
                echo "📁 Navigating to toolbox directory..."
                cd toolbox
                
                echo "🔄 Updating repository..."
                git pull origin master
                
                echo "🏃 Running build retrieval..."
                cd tools/retrieve_builds
                python -u retrieve_sd_builds.py --branch ${config.branch} --server ${config.serverHost}
EOF
            echo "✅ Build retrieval completed"
        """
    }
    
    /**
     * Get build status
     */
    def getBuildStatus() {
        return script.currentBuild.result ?: 'SUCCESS'
    }
    
    /**
     * Set build status
     */
    def setBuildStatus(String status) {
        script.currentBuild.result = status
    }
}