with open('src/app/ui/panels/StudentPanel.java', 'r', encoding='utf-8') as f:
    text = f.read().replace('\r\n', '\n')

old_launch = """                    Process process = new ProcessBuilder(command)
                            .directory(scriptPath.getParent().toFile())
                            .redirectErrorStream(true)
                            .start();
                    String output;"""

new_launch = """                    Process process = new ProcessBuilder(command)
                            .directory(scriptPath.getParent().toFile())
                            .redirectErrorStream(true)
                            .start();
                    SwingUtilities.invokeLater(progress::dispose);
                    String output;"""

assert old_launch in text, 'old_launch not found'
text = text.replace(old_launch, new_launch, 1)

with open('src/app/ui/panels/StudentPanel.java', 'w', encoding='utf-8') as f:
    f.write(text)
print('StudentPanel launchSimulation updated successfully!')
