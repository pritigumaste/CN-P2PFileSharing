# compile all sources
all: 
	javac -cp .:jsch-0.1.54.jar ./code/*.java *.java 

# Clean target to remove compiled files
clean:
	rm -f ./*.class ./code/*.class
