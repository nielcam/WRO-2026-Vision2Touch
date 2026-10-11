from subprocess import call
import time


def left():
    call(["python", "Results x axis right.py"])


def right():
    call(["python", "Result x axis left.py"])


def Forward():
    call(["python", "results Z axis forward.py"])


def print_letter(letter):
    letter = letter.upper()

    if letter == "A":
        call(["python", "solenoid printer (A).py"])
        
    elif letter == "$":
        call(["python", "results Z axis forward.py"])

    elif letter == "B":
        call(["python", "solenoid printer (B).py"])

    elif letter == "C":
        call(["python", "solenoid printer (C).py"])

    elif letter == "D":
        call(["python", "solenoid printer (D).py"])
        
    elif letter == "E":
        call(["python", "solenoid printer (E).py"])
        
    elif letter == "F":
        call(["python", "solenoid printer (F).py"])
        
    elif letter == "G":
        call(["python", "solenoid printer (G).py"])
        
    elif letter == "H":
        call(["python", "solenoid printer (H).py"])
        
    elif letter == "I":
        call(["python", "solenoid printer (I).py"])
        
    elif letter == "J":
        call(["python", "solenoid printer (J).py"])
        
    elif letter == "K":
        call(["python", "solenoid printer (K).py"])
        
    elif letter == "L":
        call(["python", "solenoid printer (L).py"])
        
    elif letter == "M":
        call(["python", "solenoid printer (M).py"])
        
    elif letter == "N":
        call(["python", "solenoid printer (N).py"])
        
    elif letter == "O":
        call(["python", "solenoid printer (O).py"])
        
    elif letter == "P":
        call(["python", "solenoid printer (P).py"])
        
    elif letter == "Q":
        call(["python", "solenoid printer (Q).py"])
        
    elif letter == "R":
        call(["python", "solenoid printer (R).py"])
        
    elif letter == "S":
        call(["python", "solenoid printer (S).py"])
        
    elif letter == "T":
        call(["python", "solenoid printer (T).py"])
        
    elif letter == "U":
        call(["python", "solenoid printer (U).py"])
        
    elif letter == "V":
        call(["python", "solenoid printer (V).py"])
        
    elif letter == "W":
        call(["python", "solenoid printer (W).py"])
        
    elif letter == "X":
        call(["python", "solenoid printer (X).py"])
        
    elif letter == "Y":
        call(["python", "solenoid printer (Y).py"])
        
    elif letter == "Z":
        call(["python", "solenoid printer (Z).py"])

    else:
        print(f"Skipping unsupported letter: {letter}")


word = input("What word? : ")

movement_count = 0

for letter in word:
    print(repr(letter))

    # Print the letter if it isn't a space
    if letter != " ":
        print_letter(letter)
        time.sleep(3)

    # Move left
    left()

    # Count the movement
    movement_count += 1

    # After every 10 movements
    if movement_count == 8:

        # Move down one
        Forward()

        # Move right 10 times
        for _ in range(8):
            right()

        # Reset counter
        movement_count = 0
