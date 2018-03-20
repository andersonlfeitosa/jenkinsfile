def call(body) {

    properties([
        // parameters([
        // string(
        //  defaultValue: "1.0.0-SNAPSHOT",
        //  description: 'Number of version',
        //  name: 'VERSION'
        // ),
        // string(
        //  defaultValue: "1.0.0-SNAPSHOT",
        //  description: 'Next number of version',
        //  name: 'NEXT_VERSION'
        // )
        // ])
        durabilityHint('PERFORMANCE_OPTIMIZED')
    ])
    
    node {
        // Clean workspace before doing anything
        
        deleteDir()
        def VARS = checkout scm
        def BRANCH_NAME = VARS.GIT_BRANCH
        def COMMIT_MESSAGE = sh (script: 'git log -1 --pretty=%B',returnStdout: true).trim()

        if(COMMIT_MESSAGE.contains("[maven-release-plugin]")) {
            currentBuild.result = 'FAILURE'
            sh "exit ./build.sh" 
        }

        try {
            stage('Checkout') {
                checkout scm
                echo "COMMIT_MESSAGE =  " + COMMIT_MESSAGE
                echo "parameters = " + VERSION + " e " + NEXT_VERSION
                echo "branch = " + BRANCH_NAME
            }
            stage('Build') {
                echo "Initializing Build phase"
                sh "mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true"
            }
            stage('Test') {
                if(!branch_is_feature()) {
                    echo "Initializing test phase"
                    sh "mvn test"
                }
            }
            stage ('Analyse') {
                if(!branch_is_feature()) {
                    echo "Initializing Analyse phase"
                    withSonarQubeEnv('Sonar') {
                        sh "mvn sonar:sonar"
                    }
                }
            }
            stage('Quality Gate') {
                 if(!branch_is_feature()) {
                    echo "Initializing Quality Gate phase"
                    timeout(time: 1, unit: 'HOURS') {
                        def qg = waitForQualityGate()
                        if (qg.status != 'OK') {
                            error "Pipeline aborted due to quality gate failure: ${qg.status}"
                        }
                    }
                }
            }
            stage('Archive') {
                if(branch_is_master() || branch_is_hotfix()) {
                    echo 'Initializing Archive phase'
                    sh 'mvn deploy -Dmaven.test.skip=true'
                }
            }
            stage ('Release') {
                if(VERSION != NEXT_VERSION) {
                    if(branch_is_master()) {
                        echo 'Initializing Release phase'
                        sh 'git checkout master'
                        sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                    } else if(branch_is_hotfix()) {
                        echo 'Initializing Release phase'
                        sh 'git checkout '+PBRANCH_NAME
                        sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                    }
                }
            }
                
            stage('Docker') {
                if(VERSION != NEXT_VERSION) {
                    if(branch_is_master() || branch_is_hotfix()) {
                        echo 'Initializing Docker phase'
                        //sh "mvn package docker:build docker:push"
                    }
                }
            }
        } catch (err) {
            currentBuild.result = 'FAILED'
            throw err
        }
    }


def branch_is_feature() {
    return test_branch_name("origin/feature/")
}

def branch_is_master() {
    return test_branch_name("origin/master")
}

def branch_is_develop() {
    return test_branch_name("origin/develop")
}

def branch_is_hotfix() {
    return test_branch_name("origin/hotfix/")
}

def test_branch_name(branch) {
    return VARS.GIT_BRANCH.startsWith(branch)
}

}