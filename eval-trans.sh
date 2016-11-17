
#mvn clean
#mvn dependency:copy-dependencies
#mvn compile



CP="./target/classes/:./target/dependency/*:./config/" # use this 
java -ea -Xmx40g -cp $CP edu.illinois.cs.cogcomp.xlwikifier.research.transliteration.Evaluator $1 $2 $3
