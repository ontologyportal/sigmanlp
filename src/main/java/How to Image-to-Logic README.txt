How to Image-to-Logic README:

Install SIGMANLP, sumo, and sigmakee from : https://github.com/ontologyportal. Request access from Prof. Adam Pease. 

In you environment navigate to sigmanlp/src/main/java/com/articulate/nlp.

run the command ant to compile the java code. 
make sure to update the directories where you want the data saved and update the directory which is used to hold your api key.

run the command :
java -Xmx40g -classpath "$SIGMANLP_CP" com.articulate.nlp.GenOrientations Orientations XX

where XX is the number of accepted sentences you want to generate. By default,
the manual coherence detector prompts for each sentence: enter "a" to accept it
or "d" to reject it. Rejected sentences do not count toward XX.

To disable the coherence detector and automatically accept every generated sentence:
java -Xmx40g -classpath "$SIGMANLP_CP" com.articulate.nlp.GenOrientations Orientations XX --no-coherence-check

The --no-coherence-check option must follow the sentence count. This mode does
not assess coherence; it saves XX sentences without requiring keyboard input.
Omit the option to restore manual checking. Use --help to display usage.

The generator writes Orientations-eng.txt, Orientations-log.txt, and data.json
in the current working directory. If data.json already exists, new records are
appended with IDs continuing after the largest existing ID.

Next run the command: 

java -Xmx40g -classpath "$SIGMANLP_CP" com.articulate.nlp.GenOrientationImages

this will generate the images and update the json automatically


Image Variation:
ImageVariations.py is a python file that can edit images created by the above java code. Example command below:

python3 src/main/java/com/articulate/nlp/ImageVariations.py --prompt "move the book further right from the chair" --out "Generate_a_realistic_image_of_The_Chair_is_left_of_the_Book_1.png" --outdir "./images" --image-name "edge"

This python code was not fully completed. Be careful of which directory you run it in and make sure to edit the save locations for the json and edited images to reflect your data set. Additionally, the prompt represents how you want to edit the image, the --out argument is what you want the edited image title to be, the --outdir is the directory where you want the edited image to be saved, the --image-name is the string that will be appended to existing image names (in combination with --out) to identify edited images. The example above is only designed for one data point in  the data set. For use with an entire data set, it is strongly recommended to update the code to handle any number of data points.  