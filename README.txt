First follow the instructions to install sigmakee at https://github.com/ontologyportal/sigmakee
This assumes you've also downloaded Stanford CoreNLP

cd ~/workspace/
git clone https://github.com/ontologyportal/sigmanlp
cp ~/Programs/stanford-corenlp-3.6.0.jar ~/workspace/sigmanlp/lib
cp ~/Programs/stanford-corenlp-3.6.0-models.jar ~/workspace/sigmanlp/lib
ant

If you want to run sigmanlp's web interface then

ant dist

then start tomcat and point your browser at

http://localhost:8080/sigmanlp/NLP.jsp
