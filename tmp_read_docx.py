import zipfile
import sys
try:
    doc = zipfile.ZipFile(sys.argv[1])
    xml = doc.read('word/document.xml')
    with open('tmp_doc.xml', 'wb') as f:
        f.write(xml)
except Exception as e:
    print(e)
