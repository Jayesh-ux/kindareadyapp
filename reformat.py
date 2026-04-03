import sys

def reformat_and_diagnose(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    level = 0
    new_lines = []
    for i, line in enumerate(lines):
        clean = line.strip()
        if not clean:
            new_lines.append("\n")
            continue
            
        # Closing brace reduces level for CURRENT line if it starts with it
        change_current = clean.startswith('}')
        if change_current:
            level -= 1
            
        new_lines.append("    " * max(0, level) + clean + "\n")
        
        # Opening brace after indenting
        level += clean.count('{')
        # Closing brace after indenting (if not handled already)
        if not change_current:
             level -= clean.count('}')
             
    with open(filename + ".fixed", 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    print(f"Reformatted to {filename}.fixed. Final level: {level}")

if __name__ == "__main__":
    reformat_and_diagnose(sys.argv[1])
