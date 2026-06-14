#!/bin/bash
# replace-onlyoffice.sh - Replace all OnlyOffice branding in editor

WEBAPPS="/var/www/onlyoffice/documentserver/web-apps/apps"
EDITORS="documenteditor spreadsheeteditor presentationeditor pdfeditor visioeditor"

for editor in $EDITORS; do
    DIR="$WEBAPPS/$editor/main"
    [ -d "$DIR" ] || continue

    for js in "$DIR/app.js" "$DIR/code.js" "$DIR/ie/app.js" "$DIR/ie/code.js"; do
        [ -f "$js" ] || continue
        echo "Processing: $js"

        sed -i 's/txtPoweredBy:"Powered by"/txtPoweredBy:""/g' "$js"
        sed -i 's/txtEdition:"Integration Edition "/txtEdition:""/g' "$js"
        sed -i 's/"ONLYOFFICE"/"MiaotongDoc"/g' "$js"
        sed -i 's/ONLYOFFICE Document Server/MiaotongDoc Editor/g' "$js"
        sed -i 's/ONLYOFFICE Docs/MiaotongDoc Editor/g' "$js"
        sed -i 's|https://www.onlyoffice.com|#|g' "$js"
        sed -i 's|https://helpcenter.onlyoffice.com|#|g' "$js"
        sed -i 's|https://support.onlyoffice.com|#|g' "$js"
        sed -i 's|https://feedback.onlyoffice.com|#|g' "$js"
        sed -i 's|https://forum.onlyoffice.com|#|g' "$js"
        sed -i 's|mailto:support@onlyoffice.com||g' "$js"
        sed -i 's|mailto:sales@onlyoffice.com||g' "$js"
        sed -i 's|http://www.onlyoffice.com|#|g' "$js"

        gzip -kf "$js"
    done
done

# Process embed/forms
for editor in $EDITORS; do
    for dir in "$WEBAPPS/$editor/embed" "$WEBAPPS/$editor/forms"; do
        [ -d "$dir" ] || continue
        for js in "$dir"/*.js; do
            [ -f "$js" ] || continue
            echo "Processing: $js"
            sed -i 's/"ONLYOFFICE"/"MiaotongDoc"/g' "$js"
            sed -i 's/ONLYOFFICE Document Server/MiaotongDoc Editor/g' "$js"
            sed -i 's|https://www.onlyoffice.com|#|g' "$js"
            gzip -kf "$js"
        done
    done
done

# Process locale JSON files
find "$WEBAPPS" -name "*.json" -path "*/locale/*" -exec grep -l -i "onlyoffice" {} \; 2>/dev/null | while read f; do
    echo "Processing locale: $f"
    sed -i 's/ONLYOFFICE/MiaotongDoc/g' "$f"
    sed -i 's/OnlyOffice/MiaotongDoc/g' "$f"
done

# Process common JS
for js in /var/www/onlyoffice/documentserver/web-apps/apps/common/main/resources/js/*.js; do
    [ -f "$js" ] || continue
    if grep -qi "onlyoffice" "$js" 2>/dev/null; then
        echo "Processing common: $js"
        sed -i 's/"ONLYOFFICE"/"MiaotongDoc"/g' "$js"
        sed -i 's/txtPoweredBy:"Powered by"/txtPoweredBy:""/g' "$js"
        gzip -kf "$js"
    fi
done

echo "Done!"
