First follow the instructions to install sigmakee at https://github.com/ontologyportal/sigmakee
This assumes you've also downloaded Stanford CoreNLP.

cd ~
echo "export SIGMA_SRC=~/workspace/sigmakee" >> .bashrc
source .bashrc
cd ~/workspace/
git clone https://github.com/ontologyportal/sigmanlp
cd ~/Programs
wget 'http://nlp.stanford.edu/software/stanford-corenlp-full-2015-12-09.zip'
unzip stanford-corenlp-full-2015-12-09.zip
rm stanford-corenlp-full-2015-12-09.zip
cd ~/Programs/stanford-corenlp-full-2015-12-09/
unzip stanford-corenlp-3.6.0-models.jar
cp ~/Programs/stanford-corenlp-full-2015-12-09/stanford-corenlp-3.6.0.jar ~/workspace/sigmanlp/lib
cp ~/Programs/stanford-corenlp-full-2015-12-09/stanford-corenlp-3.6.0-models.jar ~/workspace/sigmanlp/lib
cd ~/workspace/sigmanlp
ant

Then follow the steps in "Account Management" below before proceeding

In your .bashrc you'll need to have a greater heap space allocation than for sigmakee alone

export CATALINA_OPTS="$CATALINA_OPTS -Xms1000M -Xmx5000M"

If you want to run sigmanlp's web interface then

ant dist

Start Tomcat with
$CATALINA_HOME/bin/startup.sh

http://localhost:8080/sigmanlp/NLP.jsp

Account Management
==================

Add the following to your $CATALINA_HOME/conf/context.xml

<Context crossContext="true">

