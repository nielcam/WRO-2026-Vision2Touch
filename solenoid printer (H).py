from subprocess import call
import time

def solenoid_1():
    call(["python", "solenoid 1.py"])
    
def solenoid_0():
    call(["python", "solenoid 0.py"])
    
def solenoid_3():
    call(["python", "solenoid 3.py"])
    
def solenoid_2():
    call(["python", "solenoid 2.py"])
    
def solenoid_4():
    call(["python", "solenoid 4.py"])
  
def solenoid_5():
    call(["python", "solenoid 5.py"])
    
#open_py_left()
#open_py_right()
#open_py_back()
#open_py_forward()
   
for i in range (3):
    #solenoid_0() 
    #time.sleep(0.1)
    solenoid_1()
    #time.sleep(0.1)
    solenoid_2()
    #time.sleep(0.1)
    solenoid_3()
    #time.sleep(0.1)
    #solenoid_4()
    #time.sleep(0.1)
    #solenoid_5()
    #time.sleep(0.1)




