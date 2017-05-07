First follow the instructions to install sigmakee at https://github.com/ontologyportal/sigmakee
This assumes you've also downloaded Stanford CoreNLP

cd ~/workspace/
git clone https://github.com/ontologyportal/sigmanlp
cp ~/Programs/stanford-corenlp-3.6.0.jar ~/workspace/sigmanlp/lib
cp ~/Programs/stanford-corenlp-3.6.0-models.jar ~/workspace/sigmanlp/lib
cd sigmanlp
ant

In your .bashrc you'll need to have a greater heap space allocation than for sigmakee alone

export CATALINA_OPTS="$CATALINA_OPTS -Xms1000M -Xmx5000M"

If you want to run sigmanlp's web interface then

ant dist

then start tomcat and point your browser at

http://localhost:8080/sigmanlp/NLP.jsp
