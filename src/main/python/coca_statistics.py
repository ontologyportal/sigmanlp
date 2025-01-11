import sqlite3
import uuid
import os


'''
In the second version of COCA_STATISTICS, we can have the same word in the dictionary as long as it has a different possition.
For example we can have BOTH Love, noun and Love, verb something that wasn't possible in the first version since it only checked the
root value and not the type of the word.
'''


filepath_base = os.environ.get("ONTOLOGYPORTAL_GIT")
COCA_filepath = os.path.join(filepath_base, "sigmanlp/corpora/COCA")

#DB connection
conn = sqlite3.connect(COCA_filepath + '/word_pairs.db')

verb_nouns_pairs = {}
nouns_pairs = {}
verbs_pairs = {}

dictionary = {}

def split_sentences(filename):

    sentences = []
    current_sentence = []

    # Open and read the file
    with open(filename, 'r', encoding='latin1') as file:
        for line in file:
            # Split the line into parts
            parts = line.strip().split('\t')

            # We have 2 kind of files with different number of columns, 3 columns and 5 columns
            if len(parts) != 3 and len(parts) != 5:
                continue
            elif len(parts) == 3:
                word_info = [parts[0], parts[1], parts[2]]
                current_sentence.append(word_info)
            elif len(parts) == 5:
                # Extract word, root, and POS tag
                word_info = [parts[2], parts[3], parts[4]]
                current_sentence.append(word_info)

            # Check if this word ends the sentence
            if word_info[0] in {'.', '!', '?', '#', '<p>', '...', '....'} :
                sentences.append(current_sentence)
                current_sentence = []

    # Add the last sentence if it doesn't end with punctuation
    if current_sentence:
        sentences.append(current_sentence)
    return sentences


def create_relations(sentences):

    for sentence in sentences:
        verbs = []
        nouns = []

        for word_info in sentence:
            word, root, pos = word_info
            if root == '':
                continue
            if pos.lower().startswith('vb') or pos.lower().startswith('vv'):
                pos = 'verb'
                if (root, pos) not in dictionary:
                    dictionary[(root, pos)] = str(uuid.uuid4())
                verbs.append(dictionary[(root,pos)])
            elif pos.lower().startswith('nn'):
                pos = 'noun'
                if (root, pos) not in dictionary:
                    dictionary[(root, pos)] = str(uuid.uuid4())
                nouns.append(dictionary[(root,pos)])
            elif pos.lower().startswith('np'): # names
                pos = 'noun-phrase'
                if (root, pos) not in dictionary:
                    dictionary[(root, pos)] = str(uuid.uuid4())

        # Create pairs of verbs
        for i in range(len(verbs)):
            for j in range(i + 1, len(verbs)):
                # Sort the noun pair to ensure consistency in storage
                verb_tuple = tuple(sorted([verbs[i], verbs[j]]))
                verbs_pairs[verb_tuple] = verbs_pairs.get(verb_tuple, 0) + 1  # Noun-Noun pair

        for i in range(len(verbs)):
            for j in range(len(nouns)):
                # Sort the noun pair to ensure consistency in storage
                verb_noun_tuple = tuple(sorted([verbs[i], nouns[j]]))
                verb_nouns_pairs[verb_noun_tuple] = verb_nouns_pairs.get(verb_noun_tuple, 0) + 1  # Verb-Noun pair

        for i in range(len(nouns)):
            for j in range(i + 1, len(nouns)):
                # Sort the noun pair to ensure consistency in storage
                nouns_pair_tuple = tuple(sorted([nouns[i], nouns[j]]))
                nouns_pairs[nouns_pair_tuple] = nouns_pairs.get(nouns_pair_tuple, 0) + 1  # Noun-Noun pair


def insert_dictionary():

    print('Process of inserting ' + str(len(dictionary)) + ' Dictionary values to DB started:')

    for key, value in dictionary.items():
        try:
            cursor.execute('''
      INSERT INTO Word (id, root, pos)
      VALUES (?, ?, ?)
      ''', (value, key[0], key[1]))
        except Exception as e:
            print(f"An error occurred at insert_dictionary: {e}: the word {key[0]}, {key[1], {value}}")
    conn.commit()
    print('Insert in dictionary completed')


def insert_relations():

    print('Process of inserting ' + str(len(verbs_pairs)) + ' verbs_pairs values to DB started:')
    for key, value in verbs_pairs.items():
        try:
            cursor.execute('''SELECT count FROM WordPair WHERE word1_id = ? AND word2_id = ?''', (key[0], key[1]))
            result = cursor.fetchone()

            if result:
                cursor.execute('''UPDATE WordPair SET count = count + 1 WHERE word1_id = ? AND word2_id = ?''', (key[0], key[1]))
            else:
                cursor.execute('''INSERT INTO WordPair (word1_id, word2_id, count) VALUES (?, ?, ?)''', (key[0], key[1], value))
        except sqlite3.IntegrityError as e:
            print(f"IntegrityError - verbs_pairs: {e}")
        except Exception as e:
            print(f"An error occurred at verbs_pairs: {e}")
    conn.commit()
    print('Process of inserting the verbs_pairs values to DB completed:')

    print('Process of inserting the verb_nouns_pairs values to DB started:')
    for key, value in verb_nouns_pairs.items():
        try:
            cursor.execute('''SELECT count FROM WordPair WHERE word1_id = ? AND word2_id = ?''', (key[0], key[1]))
            result = cursor.fetchone()

            if result:
                cursor.execute('''UPDATE WordPair SET count = count + 1 WHERE word1_id = ? AND word2_id = ?''', (key[0], key[1]))
            else:
                cursor.execute('''INSERT INTO WordPair (word1_id, word2_id, count) VALUES (?, ?, ?)''', (key[0], key[1], value))
        except sqlite3.IntegrityError as e:
            print(f"IntegrityError - verb_nouns_pairs: {e}")
        except Exception as e:
            print(f"An error occurred at verb_nouns_pairs: {e}")
    conn.commit()
    print('Process of inserting the verb_nouns_pairs values to DB completed:')

    print('Process of inserting the nouns_pairs values to DB started:')
    for key, value in nouns_pairs.items():
        try:
            cursor.execute('''SELECT count FROM WordPair WHERE word1_id = ? AND word2_id = ?''', (key[0], key[1]))
            result = cursor.fetchone()
            if result:
                cursor.execute('''UPDATE WordPair SET count = count + 1 WHERE word1_id = ? AND word2_id = ?''', (key[0], key[1]))
            else:
                cursor.execute('''INSERT INTO WordPair (word1_id, word2_id, count) VALUES (?, ?, ?)''', (key[0], key[1], value))
        except sqlite3.IntegrityError as e:
            print(f"IntegrityError - nouns_pairs: {e}")
        except Exception as e:
            print(f"An error occurred at nouns_pairs: {e}")

    conn.commit()
    print('Process of inserting the nouns_pairs values to DB completed:')



# Using the special variable
# __name__
if __name__=="__main__":

    cursor = conn.cursor()

    cursor.execute('''
    CREATE TABLE IF NOT EXISTS Word (
        id TEXT PRIMARY KEY,  -- Use TEXT to store UUIDs
        root TEXT NOT NULL,
        pos TEXT NOT NULL)
    ''')

    cursor.execute('''
    CREATE TABLE IF NOT EXISTS WordPair (
        word1_id TEXT,
        word2_id TEXT,
        count INTEGER DEFAULT 0,
        PRIMARY KEY (word1_id, word2_id),
        FOREIGN KEY (word1_id) REFERENCES Word(id),
        FOREIGN KEY (word2_id) REFERENCES Word(id))
    ''')

    for root, dirs, files in os.walk(COCA_filepath):
        counter = 0
        for file in files:
            if file.endswith('.txt'):
                if not (file == "nouns.txt" or file == "verbs.txt"):
                    filename = os.path.join(root, file)
                    counter += 1
                    print(f'Processing file {counter}/{len(files)} | name: {filename}')
                    sentences = split_sentences(filename)
                    create_relations(sentences)
                else:
                    print("Skipped: " + file + ". This file is generated from, but not part of COCA.")
        for dir in dirs:
            if dir.endswith('.zip'):
                print("Found " + dir + ". File not processed. Are you sure you unzipped it?")

    insert_dictionary()

    insert_relations()





