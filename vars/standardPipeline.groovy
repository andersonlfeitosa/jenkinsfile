def call(body) {

    properties([
        durabilityHint('PERFORMANCE_OPTIMIZED')
    ])
    
    node {
        // Clean workspace before doing anything
        deleteDir()

        try {
            stage('Checkout') {
                echo "PARAMETERS = ${VERSION} e ${NEXT_VERSION}"
                checkout scm
            }
            stage('Build') {
                echo "Initializing Build phase"
                sh "mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true"
            }
            stage('Test') {
                if(env.BRANCH_NAME != "**/feature/*") {
                    echo "Initializing test phase"
                    sh "mvn test"
                }
            }
            stage ('Analyse') {
                if(env.BRANCH_NAME != "**/feature/*") {
                    echo "Initializing Analyse phase"
                    //withSonarQubeEnv('Sonar') {
                        //sh "mvn sonar:sonar"
                    //}
                }
            }
            stage('Quality Gate') {
                 echo "Initializing Quality Gate phase"
               // if(env.BRANCH_NAME != "**/feature/*") {
                    //timeout(time: 1, unit: 'HOURS') {
                      //  def qg = waitForQualityGate()
                       // if (qg.status != 'OK') {
                         //   error "Pipeline aborted due to quality gate failure: ${qg.status}"
                        //}
                    //}
               // }
            }
            stage('Archive') {
                if(env.BRANCH_NAME != "**/feature/*") {
                    echo 'Initializing Archive phase'
                    sh 'mvn deploy -Dmaven.test.skip=true'
                } 
            }
            stage ('Release') {       
                //if((env.BRANCH_NAME == "**/master" || en.BRANCH_NAME == "**/hotfix") && ${next_version} != ${version}) {
                    echo 'Initializing Release phase'
                    sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                //} 
            }
            stage('Docker') {
                //sh "mvn package docker:build docker:push"
            }
        } catch (err) {
            currentBuild.result = 'FAILED'
            throw err
        }
    }
}
