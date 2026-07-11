#!/usr/bin/env python3
"""修改 OnlyOffice 新建文档模板的 app.xml 元数据"""
import zipfile, os, tempfile, shutil, glob

TMPL_DIR = "/var/www/onlyoffice/documentserver/document-templates"
NEW_APP_XML = b'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>MiaotongDoc</Application><AppVersion>1.0</AppVersion></Properties>'''

count = 0
for ext in ("*.docx", "*.xlsx", "*.pptx"):
    for zpath in glob.glob(os.path.join(TMPL_DIR, "**", ext), recursive=True):
        try:
            tmp = zpath + ".tmp"
            modified = False
            with zipfile.ZipFile(zpath, "r") as zin, zipfile.ZipFile(tmp, "w") as zout:
                for item in zin.infolist():
                    data = zin.read(item.filename)
                    if item.filename == "docProps/app.xml":
                        data = NEW_APP_XML
                        modified = True
                    zout.writestr(item, data)
            if modified:
                shutil.move(tmp, zpath)
                count += 1
                print(f"  updated: {zpath}")
            else:
                os.remove(tmp)
        except Exception as e:
            print(f"  skip {zpath}: {e}")
            if os.path.exists(tmp):
                os.remove(tmp)

print(f"Done: {count} templates updated")
