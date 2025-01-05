import sys
import os
import re
import ollama

def reword_sentences(eng_chunk, logic_chunk, model):
    '''Takes in two lists, one of sentences and the other of logic equivalents, and uses model passed to reword sentence and
    associate it with the passed equivalent.'''

    if len(eng_chunk) != len(logic_chunk):
        print("Length of english and logic chunks do not match... Exiting")
        sys.exit(1)
    
    sentence_pairs = []
    for i in range(len(eng_chunk)):
        current_sentence = eng_chunk[i]

        prompt = f"Reword the following sentence while retaining the meaning. The sentence you return should be logically equivalent to the original sentence. Respond with JUST the reworded sentence, NOTHING ELSE. If the sentence cannot be reworded, reply 'Cannot comply.' The sentence is '{current_sentence}'"

        response = ollama.chat(
            model=model,
            messages=[
                {
                    "role": "user",
                    "content": prompt
                },
            ],
            options={
                "temperature": 0.0,  
            },
        )

        reworded_sentence = response["message"]["content"].replace("\n", " ")

        to_check_reworded = ['cannot comply', '(', 'i cannot', "i can't"]     # if the model returns some of these phrases or chars, it is likely that the sentence cannot be reworded for whatever reason
        to_check_original = ['is an instance of', 'is a subclass of']     # ignore subclass / instance declarations

        if any(word in reworded_sentence.lower() for word in to_check_reworded) or any(word in current_sentence.lower() for word in to_check_original):
            with open('error_log.txt', "a") as f:
                f.write(f'Original: +  {current_sentence}"\n" + Output: + {reworded_sentence}\n\n')
        else:
            #print(f'Original: {current_sentence}\n, Reworded: {reworded_sentence}\n')
            sentence_pairs.append((reworded_sentence, logic_chunk[i]))

    return sentence_pairs

if __name__ == "__main__":


    ### Error checking
    
    if len(sys.argv) != 4:
        print("Usage: python main.py <argument_1_file> <argument_2_file> <model>")
        sys.exit(1)

    english_file = sys.argv[1]
    logic_file = sys.argv[2]
    model = sys.argv[3]

    if not os.path.exists(english_file):
        print("File {} does not exist".format(english_file))
        sys.exit(1)

    if not os.path.exists(logic_file):
        print("File {} does not exist".format(logic_file))
        sys.exit(1)

    eng_line_count = 0
    with open(english_file, "r") as f:
        for line in f:
            eng_line_count += 1

    logic_line_count = 0
    with open(logic_file, "r") as f:
        for line in f:
            logic_line_count += 1

    if eng_line_count != logic_line_count:
        print("Number of lines in {} and {} do not match".format(english_file, logic_file))
        sys.exit(1)



    #### Process the file in chunks of 1000 lines

    for i in range(0, eng_line_count, 1000):
        if i + 1000 < eng_line_count:
            end = i + 1000
        else:
            end = eng_line_count

        eng_chunk = []
        logic_chunk = []

        with open(english_file, "r") as f:
            for j, line in enumerate(f):
                if j >= i and j < end:
                    eng_chunk.append(line.strip())
        
        with open(logic_file, "r") as f:
            for j, line in enumerate(f):
                if j >= i and j < end:
                    logic_chunk.append(line.strip())

        sentence_pairs = reword_sentences(eng_chunk, logic_chunk, model)

        for j in range(len(reworded_sentences)):
            reworded_sentence = reworded_sentences[j]
            associate_logic = logic_chunk[j]

            with open('test_eng.txt', "a") as f:
                f.write(reworded_sentence + "\n")

            with open(test_log.txt, "a") as f:
                f.write(associate_logic + "\n")



