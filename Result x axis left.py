import RPi.GPIO as GPIO
import time

# -----------------------
# GPIO Pins
# -----------------------
STEP_PIN = 18
DIR_PIN = 23
ENABLE_PIN = 24

# -----------------------
# Settings
# -----------------------
STEPS = 950    #8500 for full length     # Number of steps to move
STEP_DELAY = 0.0001    # Seconds between pulses

GPIO.setmode(GPIO.BCM)

GPIO.setup(STEP_PIN, GPIO.OUT)
GPIO.setup(DIR_PIN, GPIO.OUT)
GPIO.setup(ENABLE_PIN, GPIO.OUT)

# Enable driver (LOW = enabled on most TMC2209 boards)
GPIO.output(ENABLE_PIN, GPIO.LOW)

# Set direction
GPIO.output(DIR_PIN, GPIO.LOW)

print("Motor Moving...")

for i in range(STEPS):
    GPIO.output(STEP_PIN, GPIO.HIGH)
    time.sleep(STEP_DELAY)

    GPIO.output(STEP_PIN, GPIO.LOW)
    time.sleep(STEP_DELAY)

print("Finished.")

# Disable driver
GPIO.output(ENABLE_PIN, GPIO.LOW)

GPIO.cleanup()