# WRO-2026-Vision2Touch
WRO 2026 Vision2Touch


# Vision2Touch Braille Printer

## Overview
This branch contains multiple Python scripts developed to operate the Vision2Touch Braille printer and control its various components. The scripts work together to coordinate print-bed movement and solenoid actuation to produce Braille characters.

## Scripts

- `input.py` – The main control script, combining individual movements and solenoid operations into a coordinated printing sequence.
- `solenoid printer (A).py` – `solenoid printer (Z).py` – Controls the printing sequence for individual letters of the alphabet.
- `Solenoid 0.py` – `Solenoid 5.py` – Controls each solenoid individually, forming the basic building blocks for printing Braille characters.
- `Results x axis right.py` – Moves the print bed to the right along the X-axis.
- `results x axis left.py` – Moves the print bed to the left along the X-axis.
- `Results Z axis forward.py` – Moves the print bed forward along the Z-axis.
- `Results Z axis.py` – Moves the print bed backwards along the Z-axis.

## Working Principle

The printer software uses the individual solenoid control scripts (`Solenoid 0.py` to `Solenoid 5.py`) as the basic building blocks for producing Braille characters. These solenoids are activated in specific combinations to form individual letters.

The main script, `input.py`, receives the input and calls the corresponding letter-printing script. Once a letter has been printed, the software uses the axis movement scripts to reposition the print bed for the next character.

This process repeats for each letter in the input, coordinating solenoid activation and print-bed movement to produce a sequence of Braille characters.

## Notes

This branch is intended for developing and testing the Vision2Touch Braille printer control software, with the possibility of adapting the code for use in similar projects.
