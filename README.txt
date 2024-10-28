First follow the instructions to install sigmakee at https://github.com/ontologyportal/sigmakee
Check the version number of the CoreNLP zip that you download and unzip and modify paths
accordingly

cd ~
echo "export SIGMA_SRC=~/workspace/sigmakee" >> .bashrc
echo "export CORPORA=~/workspace/sigmanlp/corpora" >> .bashrc
source .bashrc
cd ~/workspace/
git clone https://github.com/ontologyportal/sigmanlp
cd ~/Programs
wget 'https://huggingface.co/stanfordnlp/CoreNLP/resolve/main/stanford-corenlp-latest.zip'
unzip stanford-corenlp-4.3.1.zip
rm stanford-corenlp-latest.zip
cd ~/Programs/stanford-corenlp-full-4.3.1/
cp ~/Programs/stanford-corenlp-4.3.1/stanford-corenlp-4.3.1.jar ~/workspace/sigmanlp/lib
cp ~/Programs/stanford-corenlp-4.3.1/stanford-corenlp-4.3.1-models.jar ~/workspace/sigmanlp/lib
cd ~/workspace/sigmanlp
ant

Then follow the steps in "Account Management" below before proceeding

In your .bashrc you'll need to have a greater heap space allocation than for sigmakee alone

export CATALINA_OPTS="$CATALINA_OPTS -Xms1000M -Xmx7g"

export ONTOLOGYPORTAL_GIT="/home/user/workspace"

Add the following line to your $SIGMA_HOME/KBs/config.xml file, but replace '~' with the full path
  <preference name="englishPCFG" value="~/Programs/stanford-corenlp-full-2018-01-31" />

If you want to run sigmanlp's web interface then

ant dist

Start Tomcat with
$CATALINA_HOME/bin/startup.sh

http://localhost:8080/sigmanlp/NLP.jsp

If you want to make a link to the NLP tools available from Sigma's various jsp pages then include
the following in your config.xml

  <preference name="nlpTools" value="yes" />

To run on the command line, try (changing to your paths)

java -Xmx7g -classpath /home/user/workspace/sigmanlp/build/classes:
/home/user/workspace/sigmanlp/build/lib/* com.articulate.nlp.semRewrite.Interpreter -i


Account Management
==================

Add the following to your $CATALINA_HOME/conf/context.xml

<Context crossContext="true">


jUnit=============

java -Xmx10g -classpath /home/user/workspace/sigmanlp/build/classes:
/home/user/workspace/sigmanlp/build/lib/*:/home/user/workspace/sigmanlp/lib/*
org.junit.runner.JUnitCore com.articulate.nlp.semRewrite.RunAllUnitSemRewrite

java -Xmx10g -classpath /home/user/workspace/sigmanlp/build/classes:
/home/user/workspace/sigmanlp/build/lib/*:/home/user/workspace/sigmanlp/lib/*
org.junit.runner.JUnitCore com.articulate.nlp.semRewrite.RunAllSemRewriteIntegTest
