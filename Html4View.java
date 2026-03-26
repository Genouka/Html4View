// SPDX-License-Identifier: MPL-2.0
/*
  This is part of closed-source project "RareProBrowser"
     BY 秋冥散雨_GenOuka

  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at https://mozilla.org/MPL/2.0/.
*/
package com.yuanwow.html4view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.text.LineBreaker;
import android.net.Uri;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Html4View extends LinearLayout {

    private static final String TAG = "Html4View";
    private OnLinkClickListener linkClickListener
    private ImageLoader imageLoader;
    private int defaultTextColor = Color.BLACK;
    private int defaultBackgroundColor = Color.TRANSPARENT;
    public final static float defaultBaseTextSize = 12f;
    private float baseTextSize = defaultBaseTextSize;

    public interface OnLinkClickListener {
        void onLinkClick(String url);
    }

    public interface ImageLoader {
        void loadImage(ImageView imageView, String url, String alt);
    }

    public Html4View(Context context) {
        super(context);
        init();
    }

    public Html4View(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Html4View(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setPadding(16, 16, 16, 16);
    }

    public void setHtml(String html,String baseUri) {
        removeAllViews();
        try 
            Document doc = Jsoup.parse(html, baseUri, Parser.htmlParser());
            Element body = doc.body();
            processBodyAttributes(body);
            for (Element child : body.children()) {
                View view = parseElement(child);
                if (view != null) {
                    addView(view);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse HTML error", e);
            TextView errorView = createTextView("解析错误: " + e.getMessage(), null);
            addView(errorView);
        }
    }
    public void setText(CharSequence text){
        removeAllViews();
        TextView textView = createTextView(text, null);
        addView(textView);
    }
    private void processBodyAttributes(Element body) {
        if (body.hasAttr("bgcolor")) {
            String bgColor = body.attr("bgcolor");
            defaultBackgroundColor = parseColor(bgColor);
            setBackgroundColor(defaultBackgroundColor);
        }
        if (body.hasAttr("text")) {
            defaultTextColor = parseColor(body.attr("text"));
        }
    }
    private View parseElement(Element element) {
        String tagName = element.tagName().toLowerCase();

        switch (tagName) {
            case "p":
                return createParagraph(element);
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                return createHeading(element, tagName);
            case "div":
                return createContainer(element);
            case "ul":
            case "ol":
                return createList(element, tagName.equals("ol"));
            case "img":
                return createImage(element);
            case "hr":
                return createHorizontalRule();
            case "br":
                return createLineBreak();
            case "blockquote":
                return createBlockquote(element);
            case "center":
                return createCentered(element);
            case "pre":
                return createPreformatted(element);
            case "table":
                return createTable(element);
            case "tr":
                // tr 通常由 table 直接处理，这里返回null避免重复渲染
                return null;
            case "td":
            case "th":
                // td/th 由 tr 处理，这里返回null
                return null;
            default:
                // 对于未知标签，尝试解析其子元素
                if (element.children().isEmpty() && element.text().isEmpty()) {
                    return null;
                }
                return createGenericContainer(element);
        }
    }
    private View createTable(Element element) {
        LinearLayout tableContainer = new LinearLayout(getContext());
        tableContainer.setOrientation(VERTICAL);
        tableContainer.setPadding(8, 8, 8, 8);
        boolean hasBorder = element.hasAttr("border");
        int borderWidth = 0;
        if (hasBorder) {
            try {
                borderWidth = Integer.parseInt(element.attr("border"));
            } catch (NumberFormatException ignored) {
                borderWidth = 1;
            }
        }
        Elements rows = element.select("tr");
        if (rows.isEmpty()) {
            return null;
        }
        int maxCols = 0;
        for (Element row : rows) {
            int cols = row.select("td, th").size();
            if (cols > maxCols) maxCols = cols;
        }
        for (Element row : rows) {
            LinearLayout rowLayout = createTableRow(row, maxCols, hasBorder, borderWidth);
            if (rowLayout != null) {
                tableContainer.addView(rowLayout);
            }
        }

        return tableContainer;
    }
    private LinearLayout createTableRow(Element row, int maxCols, boolean hasBorder, int borderWidth) {
        LinearLayout rowLayout = new LinearLayout(getContext());
        rowLayout.setOrientation(HORIZONTAL);
        LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowLayout.setLayoutParams(rowParams);

        Elements cells = row.select("td, th");

        for (int i = 0; i < cells.size(); i++) {
            Element cell = cells.get(i);
            boolean isHeader = cell.tagName().equalsIgnoreCase("th");
            int colspan = 1;
            if (cell.hasAttr("colspan")) {
                try {
                    colspan = Integer.parseInt(cell.attr("colspan"));
                    if (colspan < 1) colspan = 1;
                } catch (NumberFormatException ignored) {}
            }
            TextView cellView = createTableCell(cell, isHeader, hasBorder, borderWidth);
            LayoutParams cellParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, colspan);
            cellView.setLayoutParams(cellParams);

            rowLayout.addView(cellView);
        }
        int currentCols = cells.size();
        for (int i = currentCols; i < maxCols; i++) {
            TextView emptyCell = new TextView(getContext());
            emptyCell.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            if (hasBorder) {
                emptyCell.setBackgroundColor(Color.LTGRAY);
                int padding = borderWidth > 0 ? borderWidth * 2 : 4;
                emptyCell.setPadding(padding, padding, padding, padding);
            }
            rowLayout.addView(emptyCell);
        }

        return rowLayout;
    }
    private TextView createTableCell(Element cell, boolean isHeader, boolean hasBorder, int borderWidth) {
        SpannableStringBuilder content = parseInlineElements(cell);
        TextView tv = new TextView(getContext());
        tv.setText(content);
        CSSStyle style = CSS1Parser.parse(cell, defaultTextColor);
        if("none".equals(style.display)){
            tv.setText("");
            return tv;
        }
        style.applyToTextView(tv, baseTextSize);
        if (isHeader && style.fontWeight < 700) {
            tv.setTypeface(null, Typeface.BOLD);
        }
        if (style.borderWidth > 0) {
            applyBorder(tv, style);
        } else if (hasBorder) {
            int padding = borderWidth > 0 ? borderWidth * 2 : 4;
            tv.setPadding(padding, padding, padding, padding);
            tv.setBackgroundColor(Color.WHITE);
        }
        if (cell.hasAttr("align") && style.textAlign.equals("left")) {
            String align = cell.attr("align").toLowerCase();
            applyTextAlign(tv, align);
        }
        if (cell.hasAttr("valign")) {
            String valign = cell.attr("valign").toLowerCase();
            switch (valign) {
                case "top":
                    tv.setGravity(android.view.Gravity.TOP);
                    break;
                case "bottom":
                    tv.setGravity(android.view.Gravity.BOTTOM);
                    break;
                default:
                    tv.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    break;
            }
        }
        if (cell.hasAttr("bgcolor") && style.backgroundColor == Color.TRANSPARENT) {
            tv.setBackgroundColor(parseColor(cell.attr("bgcolor")));
        }
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        return tv;
    }
    private void applyTextAlign(TextView tv, String align) {
        switch (align) {
            case "center":
                tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                break;
            case "right":
                tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                break;
            case "left":
                tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                break;
        }
    }
    private TextView createParagraph(Element element) {
        SpannableStringBuilder builder = parseInlineElements(element);
        TextView tv = createTextView(builder, element);
        tv.setPadding(0, 8, 0, 8);
        if (element.hasAttr("align")) {
            String align = element.attr("align").toLowerCase();
            switch (align) {
                case "center":
                    tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    break;
                case "right":
                    tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                    break;
                case "left":
                    tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                    break;
            }
        }

        return tv;
    }
    private TextView createHeading(Element element, String tag) {
        SpannableStringBuilder builder = parseInlineElements(element);
        TextView tv = createTextView(builder, element);

        // 根据标题级别设置大小
        float scale = 1.0f;
        switch (tag) {
            case "h1": scale = 2.0f; break;
            case "h2": scale = 1.5f; break;
            case "h3": scale = 1.17f; break;
            case "h4": scale = 1.0f; break;
            case "h5": scale = 0.83f; break;
            case "h6": scale = 0.67f; break;
        }
        tv.setTextSize(baseTextSize * scale);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 16, 0, 8);

        return tv;
    }
    private LinearLayout createContainer(Element element) {
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(VERTICAL);
        applyElementStyles(container, element);
        for (Element child : element.children()) {
            View childView = parseElement(child);
            if (childView != null) {
                container.addView(childView);
            }
        }

        return container;
    }
    private LinearLayout createList(Element element, boolean ordered) {
        LinearLayout listContainer = new LinearLayout(getContext());
        listContainer.setOrientation(VERTICAL);
        listContainer.setPadding(32, 8, 8, 8);
        Elements items = element.select("li");
        int index = 1;
        for (Element item : items) {
            LinearLayout itemLayout = new LinearLayout(getContext());
            itemLayout.setOrientation(HORIZONTAL);
            itemLayout.setPadding(0, 4, 0, 4);
            TextView marker = new TextView(getContext());
            marker.setText(ordered ? (index++ + ". ") : "• ");
            marker.setTextSize(baseTextSize);
            marker.setTextColor(defaultTextColor);
            SpannableStringBuilder content = parseInlineElements(item);
            TextView contentView = createTextView(content, item);
            contentView.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            itemLayout.addView(marker);
            itemLayout.addView(contentView);
            listContainer.addView(itemLayout);
        }
        return listContainer;
    }
    private ImageView createImage(Element element) {
        ImageView imageView = new ImageView(getContext());
        String src = element.attr("src");
        String alt = element.attr("alt");
        CSSStyle style = CSS1Parser.parse(element, defaultTextColor);
        if("none".equals(style.display)){
            return imageView;
        }
        int width = LayoutParams.WRAP_CONTENT;
        int height = LayoutParams.WRAP_CONTENT;
        if (style.width > 0) {
            width = style.width;
        } else if (element.hasAttr("width")) {
            try {
                width = Integer.parseInt(element.attr("width"));
            } catch (NumberFormatException ignored) {}
        }
        if (style.height > 0) {
            height = style.height;
        } else if (element.hasAttr("height")) {
            try {
                height = Integer.parseInt(element.attr("height"));
            } catch (NumberFormatException ignored) {}
        }
        LayoutParams params = new LayoutParams(width, height);
        params.setMargins(
                Math.max(0, style.marginLeft),
                Math.max(0, style.marginTop),
                Math.max(0, style.marginRight),
                Math.max(0, style.marginBottom)
        );
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(true);
        imageView.setPadding(
                Math.max(0, style.paddingLeft),
                Math.max(0, style.paddingTop),
                Math.max(0, style.paddingRight),
                Math.max(0, style.paddingBottom)
        );
        if (style.borderWidth > 0) {
            applyBorder(imageView, style);
        }
        if (imageLoader != null) {
            imageLoader.loadImage(imageView, src, alt);
        } else {
            imageView.setBackgroundColor(Color.LTGRAY);
        }
        return imageView;
    }
    private View createHorizontalRule() {
        View line = new View(getContext());
        line.setBackgroundColor(Color.GRAY);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, 2);
        params.setMargins(0, 16, 0, 16);
        line.setLayoutParams(params);
        return line;
    }
    private View createLineBreak() {
        View spacer = new View(getContext());
        spacer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                (int) (baseTextSize * getResources().getDisplayMetrics().scaledDensity)));
        return spacer;
    }
    private LinearLayout createBlockquote(Element element) {
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(VERTICAL);
        container.setPadding(32, 16, 16, 16);
        container.setBackgroundColor(Color.parseColor("#f0f0f0"));

        View leftBorder = new View(getContext());
        leftBorder.setBackgroundColor(Color.GRAY);
        leftBorder.setLayoutParams(new LayoutParams(4, LayoutParams.MATCH_PARENT));

        LinearLayout innerContainer = new LinearLayout(getContext());
        innerContainer.setOrientation(VERTICAL);
        innerContainer.setPadding(16, 0, 0, 0);

        for (Element child : element.children()) {
            View childView = parseElement(child);
            if (childView != null) {
                innerContainer.addView(childView);
            }
        }
        if (innerContainer.getChildCount() == 0 && !element.text().isEmpty()) {
            TextView tv = createTextView(parseInlineElements(element), element);
            innerContainer.addView(tv);
        }

        container.addView(leftBorder);
        container.addView(innerContainer);

        return container;
    }
    private LinearLayout createCentered(Element element) {
        LinearLayout container = createContainer(element);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            }
        }
        return container;
    }
    private TextView createPreformatted(Element element) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(element.text());
        TextView tv = createTextView(builder, element);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setBackgroundColor(Color.parseColor("#f5f5f5"));
        tv.setPadding(16, 16, 16, 16);
        tv.setHorizontallyScrolling(true);
        return tv;
    }
    private LinearLayout createGenericContainer(Element element) {
        return createContainer(element);
    }
    private SpannableStringBuilder parseInlineElements(Element element) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        parseInlineNodes(element, builder, new TextStyle());
        return builder;
    }
    private void parseInlineNodes(Node node, SpannableStringBuilder builder, TextStyle style) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).text();
            text = processWhiteSpace(text, style.whiteSpace);
            int start = builder.length();
            builder.append(text);
            applyStyle(builder, start, builder.length(), style);
        } else if (node instanceof Element) {
            Element element = (Element) node;
            String tag = element.tagName().toLowerCase();
            TextStyle newStyle = style.copy();
            CSSStyle cssStyle = CSS1Parser.parse(element, 0);
            if (cssStyle.color != 0) newStyle.color = cssStyle.color;
            if (cssStyle.fontSize > 0) newStyle.sizeScale = cssStyle.fontSize / baseTextSize;
            if (cssStyle.fontWeight >= 700) newStyle.bold = true;
            if (cssStyle.fontStyle) newStyle.italic = true;
            if (cssStyle.underline) newStyle.underline = true;
            if (cssStyle.lineThrough) newStyle.lineThrough = true;
            switch (tag) {
                case "b":
                case "strong":
                    newStyle.bold = true;
                    break;
                case "i":
                case "em":
                    newStyle.italic = true;
                    break;
                case "u":
                    newStyle.underline = true;
                    break;
                case "a":
                    newStyle.link = element.attr("href");
                    newStyle.underline = true;
                    newStyle.color = Color.BLUE;
                    break;
                case "font":
                    break;
                case "span":
                    break;
                case "strike":
                case "s":
                case "del":
                    newStyle.lineThrough = true;
                    break;
                case "sub":
                    newStyle.subscript = true;
                    break;
                case "sup":
                    newStyle.superscript = true;
                    break;
                case "br":
                    builder.append("\n");
                    return;
            }
            for (Node child : element.childNodes()) {
                parseInlineNodes(child, builder, newStyle);
            }
        }
    }
    private void applyStyle(SpannableStringBuilder builder, int start, int end, TextStyle style) {
        if (start >= end) return;

        if (style.bold) {
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style.italic) {
            builder.setSpan(new StyleSpan(Typeface.ITALIC), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style.underline) {
            builder.setSpan(new UnderlineSpan(), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style.lineThrough) {
            builder.setSpan(new StrikethroughSpan(), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style.subscript) {
            builder.setSpan(new SubscriptSpan(), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style.superscript) {
            builder.setSpan(new SuperscriptSpan(), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style.color != 0) {
            builder.setSpan(new ForegroundColorSpan(style.color), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style.sizeScale != 1.0f) {
            builder.setSpan(new RelativeSizeSpan(style.sizeScale), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (style.link != null) {
            final String url = style.link;
            builder.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (linkClickListener != null) {
                        linkClickListener.onLinkClick(url);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        getContext().startActivity(intent);
                    }
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(true);
                    ds.setColor(Color.BLUE);
                }
            }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    private void parseStyleAttribute(String styleAttr, TextStyle style) {
        Pattern colorPattern = Pattern.compile("color\\s*:\\s*([^;]+)");
        Matcher matcher = colorPattern.matcher(styleAttr.toLowerCase());
        if (matcher.find()) {
            style.color = parseColor(matcher.group(1).trim());
        }
    }
    private TextView createTextView(CharSequence text, Element element) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        CSSStyle style = CSS1Parser.parse(element, defaultTextColor);
        if("none".equals(style.display)){
            tv.setText("");
            return tv;
        }
        style.applyToTextView(tv, baseTextSize);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setHighlightColor(Color.TRANSPARENT);
        return tv;
    }
    private void applyElementStyles(View view, Element element) {
        if (element.hasAttr("bgcolor")) {
            view.setBackgroundColor(parseColor(element.attr("bgcolor")));
        }
        CSSStyle style = CSS1Parser.parse(element, defaultTextColor);
        if("none".equals(style.display)){
            view.setVisibility(View.GONE);
        }
        style.applyToView(view);
    }
    private int parseColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) {
            return defaultTextColor;
        }
        colorStr = colorStr.trim().toLowerCase();
        Map<String, String> colorNames = new HashMap<>();
        colorNames.put("black", "#000000");
        colorNames.put("silver", "#c0c0c0");
        colorNames.put("gray", "#808080");
        colorNames.put("white", "#ffffff");
        colorNames.put("maroon", "#800000");
        colorNames.put("red", "#ff0000");
        colorNames.put("purple", "#800080");
        colorNames.put("fuchsia", "#ff00ff");
        colorNames.put("green", "#008000");
        colorNames.put("lime", "#00ff00");
        colorNames.put("olive", "#808000");
        colorNames.put("yellow", "#ffff00");
        colorNames.put("navy", "#000080");
        colorNames.put("blue", "#0000ff");
        colorNames.put("teal", "#008080");
        colorNames.put("aqua", "#00ffff");
        if (colorNames.containsKey(colorStr)) {
            colorStr = colorNames.get(colorStr);
        }
        try {
            return Color.parseColor(colorStr);
        } catch (IllegalArgumentException e) {
            return defaultTextColor;
        }
    }
    public void setOnLinkClickListener(OnLinkClickListener listener) {
        this.linkClickListener = listener;
    }
    public void setImageLoader(ImageLoader loader) {
        this.imageLoader = loader;
    }
    public void setBaseTextSize(float sizeSp) {
        this.baseTextSize = sizeSp;
    }
    public float getBaseTextSize() {
        return this.baseTextSize;
    }
    public void setDefaultTextColor(int color) {
        this.defaultTextColor = color;
    }
    private String processWhiteSpace(String text, String whiteSpace) {
        switch (whiteSpace) {
            case "pre":
            case "pre-wrap":
                return text;
            case "nowrap":
                return text.replace("\n", " ");
            case "pre-line":
                return text.replaceAll("[ \\t]+", " ");
            case "normal":
            default:
                return text.replaceAll("\\s+", " ");
        }
    }
    private void applyBorder(View view, CSSStyle style) {
        if (style.borderWidth > 0 && !style.borderStyle.equals("none")) {
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setStroke(style.borderWidth, style.borderColor);
            drawable.setColor(style.backgroundColor);
            view.setBackground(drawable);
        }
    }
    private static class TextStyle {
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean lineThrough = false;
        boolean subscript = false;
        boolean superscript = false;
        int color = 0;
        float sizeScale = 1.0f;
        String link = null;
        String whiteSpace = "normal";
        TextStyle copy() {
            TextStyle copy = new TextStyle();
            copy.bold = this.bold;
            copy.italic = this.italic;
            copy.underline = this.underline;
            copy.lineThrough = this.lineThrough;
            copy.subscript = this.subscript;
            copy.superscript = this.superscript;
            copy.color = this.color;
            copy.sizeScale = this.sizeScale;
            copy.link = this.link;
            copy.whiteSpace = this.whiteSpace;
            return copy;
        }
    }
    private static class CSSStyle {
        String fontFamily = null;
        float fontSize = -1;
        int fontWeight = 400;
        boolean fontStyle = false;
        int color = Color.BLACK;
        int backgroundColor = Color.TRANSPARENT;
        String backgroundImage = null;
        String textAlign = "left";
        String textDecoration = "none";
        int textIndent = 0;
        float lineHeight = 1.2f;
        int letterSpacing = 0;
        int wordSpacing = 0;
        String textTransform = "none";
        String whiteSpace = "normal";
        int width = -1;
        int height = -1;
        int paddingTop = 0;
        int paddingRight = 0;
        int paddingBottom = 0;
        int paddingLeft = 0;
        int marginTop = 0;
        int marginRight = 0;
        int marginBottom = 0;
        int marginLeft = 0;
        int borderWidth = 0;
        String borderStyle = "none";
        int borderColor = Color.BLACK;
        String display = "block";
        String cssFloat = "none";
        String clear = "none";
        boolean underline = false;
        boolean lineThrough = false;
        public void applyToTextView(TextView tv, float baseTextSize) {
            if (fontSize > 0) {
                tv.setTextSize(fontSize);
            } else {
                tv.setTextSize(baseTextSize);
            }
            int style = Typeface.NORMAL;
            if (fontWeight >= 700) style |= Typeface.BOLD;
            if (fontStyle) style |= Typeface.ITALIC;
            if (fontFamily != null) {
                Typeface tf = Typeface.create(fontFamily, style);
                tv.setTypeface(tf);
            } else {
                tv.setTypeface(tv.getTypeface(), style);
            }
            tv.setTextColor(color);
            if (backgroundColor != Color.TRANSPARENT) {
                tv.setBackgroundColor(backgroundColor);
            }
            switch (textAlign) {
                case "center":
                    tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    tv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                    break;
                case "right":
                    tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                    tv.setGravity(android.view.Gravity.RIGHT);
                    break;
                case "justify":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tv.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
                    }
                    break;
            }
            tv.setLineSpacing(0, lineHeight);
            tv.setPadding(
                    Math.max(0, paddingLeft),
                    Math.max(0, paddingTop),
                    Math.max(0, paddingRight),
                    Math.max(0, paddingBottom)
            );
            ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(
                    Math.max(0, marginLeft),
                    Math.max(0, marginTop),
                    Math.max(0, marginRight),
                    Math.max(0, marginBottom)
            );
            tv.setLayoutParams(params);
        }
        public void applyToView(View view) {
            if (backgroundColor != Color.TRANSPARENT) {
                view.setBackgroundColor(backgroundColor);
            }
            view.setPadding(
                    Math.max(0, paddingLeft),
                    Math.max(0, paddingTop),
                    Math.max(0, paddingRight),
                    Math.max(0, paddingBottom)
            );
            ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(
                    Math.max(0, marginLeft),
                    Math.max(0, marginTop),
                    Math.max(0, marginRight),
                    Math.max(0, marginBottom)
            );
            view.setLayoutParams(params);
        }
    }
    private static class CSS1Parser {
        public static CSSStyle parse(Element element, int defaultColor) {
            CSSStyle style = new CSSStyle();
            style.color = defaultColor;
            if(element == null) return style;
            parseHtmlAttributes(element, style);
            if (element.hasAttr("style")) {
                parseStyleAttribute(element.attr("style"), style);
            }
            return style;
        }
        private static void parseHtmlAttributes(Element element, CSSStyle style) {
            if(element == null) return;
            if (element.hasAttr("color") || element.hasAttr("text")) {
                String color = element.hasAttr("color") ? element.attr("color") : element.attr("text");
                style.color = parseColorValue(color, style.color);
            }
            if (element.hasAttr("bgcolor")) {
                style.backgroundColor = parseColorValue(element.attr("bgcolor"), Color.TRANSPARENT);
            }
            if (element.hasAttr("size")) {
                try {
                    int size = Integer.parseInt(element.attr("size"));
                    style.fontSize = convertHtmlFontSize(size);
                } catch (NumberFormatException ignored) {}
            }
            if (element.hasAttr("face")) {
                style.fontFamily = element.attr("face");
            }
            if (element.hasAttr("align")) {
                style.textAlign = element.attr("align").toLowerCase();
            }
            if (element.hasAttr("width")) {
                style.width = parseLength(element.attr("width"));
            }
            if (element.hasAttr("height")) {
                style.height = parseLength(element.attr("height"));
            }
        }
        private static void parseStyleAttribute(String styleAttr, CSSStyle style) {
            if (styleAttr == null || styleAttr.isEmpty()) return;
            String css = styleAttr.replaceAll("/\\*.*?\\*/", "");
            String[] declarations = css.split(";");
            for (String declaration : declarations) {
                if (declaration.trim().isEmpty()) continue;
                String[] parts = declaration.split(":", 2);
                if (parts.length != 2) continue;
                String property = parts[0].trim().toLowerCase();
                String value = parts[1].trim();
                parseCSSProperty(property, value, style);
            }
        }
        private static void parseCSSProperty(String property, String value, CSSStyle style) {
            switch (property) {
                case "font-family":
                    style.fontFamily = parseFontFamily(value);
                    break;
                case "font-size":
                    style.fontSize = parseFontSize(value);
                    break;
                case "font-weight":
                    style.fontWeight = parseFontWeight(value);
                    break;
                case "font-style":
                    style.fontStyle = value.equalsIgnoreCase("italic") || value.equalsIgnoreCase("oblique");
                    break;
                case "font":
                    parseFontShorthand(value, style);
                    break;
                case "color":
                    style.color = parseColorValue(value, style.color);
                    break;
                case "background-color":
                    style.backgroundColor = parseColorValue(value, Color.TRANSPARENT);
                    break;
                case "background-image":
                    style.backgroundImage = parseUrl(value);
                    break;
                case "background":
                    parseBackgroundShorthand(value, style);
                    break;
                case "text-align":
                    style.textAlign = value.toLowerCase();
                    break;
                case "text-decoration":
                    style.textDecoration = value.toLowerCase();
                    style.underline = value.contains("underline");
                    style.lineThrough = value.contains("line-through");
                    break;
                case "text-indent":
                    style.textIndent = parseLength(value);
                    break;
                case "line-height":
                    style.lineHeight = parseLineHeight(value);
                    break;
                case "letter-spacing":
                    style.letterSpacing = parseLength(value);
                    break;
                case "word-spacing":
                    style.wordSpacing = parseLength(value);
                    break;
                case "text-transform":
                    style.textTransform = value.toLowerCase();
                    break;
                case "white-space":
                    style.whiteSpace = value.toLowerCase();
                    break;
                case "width":
                    style.width = parseLength(value);
                    break;
                case "height":
                    style.height = parseLength(value);
                    break;
                case "padding":
                    parsePaddingShorthand(value, style);
                    break;
                case "padding-top":
                    style.paddingTop = parseLength(value);
                    break;
                case "padding-right":
                    style.paddingRight = parseLength(value);
                    break;
                case "padding-bottom":
                    style.paddingBottom = parseLength(value);
                    break;
                case "padding-left":
                    style.paddingLeft = parseLength(value);
                    break;
                case "margin":
                    parseMarginShorthand(value, style);
                    break;
                case "margin-top":
                    style.marginTop = parseLength(value);
                    break;
                case "margin-right":
                    style.marginRight = parseLength(value);
                    break;
                case "margin-bottom":
                    style.marginBottom = parseLength(value);
                    break;
                case "margin-left":
                    style.marginLeft = parseLength(value);
                    break;
                case "border":
                    parseBorderShorthand(value, style);
                    break;
                case "border-width":
                    style.borderWidth = parseLength(value);
                    break;
                case "border-style":
                    style.borderStyle = value.toLowerCase();
                    break;
                case "border-color":
                    style.borderColor = parseColorValue(value, Color.BLACK);
                    break;
                case "border-top":
                case "border-right":
                case "border-bottom":
                case "border-left":
                    String[] parts = value.split("\\s+");
                    for (String part : parts) {
                        if (part.matches("\\d+(px|pt|em|%)?")) {
                            style.borderWidth = parseLength(part);
                        }
                    }
                    break;
                case "display":
                    style.display = value.toLowerCase();
                    break;
                case "float":
                    style.cssFloat = value.toLowerCase();
                    break;
                case "clear":
                    style.clear = value.toLowerCase();
                    break;
            }
        }
        private static int parseColorValue(String value, int defaultColor) {
            if (value == null || value.isEmpty()) return defaultColor;
            value = value.trim().toLowerCase();
            Map<String, String> colorNames = new HashMap<>();
            colorNames.put("black", "#000000");
            colorNames.put("silver", "#c0c0c0");
            colorNames.put("gray", "#808080");
            colorNames.put("white", "#ffffff");
            colorNames.put("maroon", "#800000");
            colorNames.put("red", "#ff0000");
            colorNames.put("purple", "#800080");
            colorNames.put("fuchsia", "#ff00ff");
            colorNames.put("green", "#008000");
            colorNames.put("lime", "#00ff00");
            colorNames.put("olive", "#808000");
            colorNames.put("yellow", "#ffff00");
            colorNames.put("navy", "#000080");
            colorNames.put("blue", "#0000ff");
            colorNames.put("teal", "#008080");
            colorNames.put("aqua", "#00ffff");
            colorNames.put("orange", "#ffa500");
            if (colorNames.containsKey(value)) {
                value = colorNames.get(value);
            }
            if (value.startsWith("rgb(")) {
                try {
                    String nums = value.substring(4, value.length() - 1);
                    String[] parts = nums.split(",");
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    return Color.rgb(r, g, b);
                } catch (Exception e) {
                    return defaultColor;
                }
            }
            try {
                return Color.parseColor(value);
            } catch (IllegalArgumentException e) {
                return defaultColor;
            }
        }
        private static int parseLength(String value) {
            if (value == null || value.isEmpty()) return -1;
            value = value.trim().toLowerCase();
            if (value.endsWith("%")) {
                try {
                    return (int) (Float.parseFloat(value.replace("%", "")) * -100);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
            value = value.replace("px", "").replace("pt", "").replace("em", "");
            try {
                float val = Float.parseFloat(value);
                return (int) val;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        private static float parseFontSize(String value) {
            value = value.toLowerCase();
            switch (value) {
                case "xx-small": return 9f;
                case "x-small": return 10f;
                case "small": return 13f;
                case "medium": return 16f;
                case "large": return 18f;
                case "x-large": return 24f;
                case "xx-large": return 32f;
            }
            if (value.equals("larger")) return 1.2f;
            if (value.equals("smaller")) return 0.8f;
            int len = parseLength(value);
            if (len > 0) return len;
            return 16f;
        }
        private static float convertHtmlFontSize(int size) {
            switch (size) {
                case 1: return 10f;
                case 2: return 13f;
                case 3: return 16f;
                case 4: return 18f;
                case 5: return 24f;
                case 6: return 32f;
                case 7: return 48f;
                default: return 16f;
            }
        }
        private static int parseFontWeight(String value) {
            if (value.equals("bold") || value.equals("bolder")) return 700;
            if (value.equals("normal")) return 400;
            if (value.equals("lighter")) return 300;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 400;
            }
        }
        private static void parseFontShorthand(String value, CSSStyle style) {
            String[] parts = value.split("\\s+");
            for (String part : parts) {
                if (part.matches("\\d+(px|pt|em)?")) {
                    style.fontSize = parseFontSize(part);
                } else if (part.equals("italic") || part.equals("oblique")) {
                    style.fontStyle = true;
                } else if (part.equals("bold")) {
                    style.fontWeight = 700;
                } else if (!part.matches("\\d+")) {
                    style.fontFamily = part.replace(",", " ");
                }
            }
        }
        private static void parseBackgroundShorthand(String value, CSSStyle style) {
            String[] parts = value.split("\\s+");
            for (String part : parts) {
                if (part.startsWith("url(")) {
                    style.backgroundImage = parseUrl(part);
                } else if (part.startsWith("#") || colorNames().containsKey(part.toLowerCase())) {
                    style.backgroundColor = parseColorValue(part, Color.TRANSPARENT);
                }
            }
        }
        private static String parseUrl(String value) {
            if (value.contains("url(")) {
                int start = value.indexOf("url(") + 4;
                int end = value.indexOf(")", start);
                String url = value.substring(start, end);
                return url.replace("\"", "").replace("'", "");
            }
            return value;
        }
        private static void parsePaddingShorthand(String value, CSSStyle style) {
            String[] parts = value.split("\\s+");
            int[] values = new int[4];
            for (int i = 0; i < parts.length && i < 4; i++) {
                values[i] = parseLength(parts[i]);
            }

            switch (parts.length) {
                case 1:
                    style.paddingTop = style.paddingRight = style.paddingBottom = style.paddingLeft = values[0];
                    break;
                case 2:
                    style.paddingTop = style.paddingBottom = values[0];
                    style.paddingRight = style.paddingLeft = values[1];
                    break;
                case 3:
                    style.paddingTop = values[0];
                    style.paddingRight = style.paddingLeft = values[1];
                    style.paddingBottom = values[2];
                    break;
                case 4:
                    style.paddingTop = values[0];
                    style.paddingRight = values[1];
                    style.paddingBottom = values[2];
                    style.paddingLeft = values[3];
                    break;
            }
        }
        private static void parseMarginShorthand(String value, CSSStyle style) {
            String[] parts = value.split("\\s+");
            int[] values = new int[4];
            for (int i = 0; i < parts.length && i < 4; i++) {
                values[i] = parseLength(parts[i]);
            }

            switch (parts.length) {
                case 1:
                    style.marginTop = style.marginRight = style.marginBottom = style.marginLeft = values[0];
                    break;
                case 2:
                    style.marginTop = style.marginBottom = values[0];
                    style.marginRight = style.marginLeft = values[1];
                    break;
                case 3:
                    style.marginTop = values[0];
                    style.marginRight = style.marginLeft = values[1];
                    style.marginBottom = values[2];
                    break;
                case 4:
                    style.marginTop = values[0];
                    style.marginRight = values[1];
                    style.marginBottom = values[2];
                    style.marginLeft = values[3];
                    break;
            }
        }
        private static void parseBorderShorthand(String value, CSSStyle style) {
            String[] parts = value.split("\\s+");
            for (String part : parts) {
                if (part.matches("\\d+(px|pt)?")) {
                    style.borderWidth = parseLength(part);
                } else if (part.equals("solid") || part.equals("dashed") || part.equals("dotted")) {
                    style.borderStyle = part;
                } else {
                    style.borderColor = parseColorValue(part, Color.BLACK);
                }
            }
        }
        private static float parseLineHeight(String value) {
            if (value.endsWith("%")) {
                try {
                    return Float.parseFloat(value.replace("%", "")) / 100f;
                } catch (NumberFormatException e) {
                    return 1.2f;
                }
            }
            try {
                return Float.parseFloat(value);
            } catch (NumberFormatException e) {
                return 1.2f;
            }
        }
        private static String parseFontFamily(String value) {
            return value.replace("\"", "").replace("'", "");
        }
        private static Map<String, String> colorNames() {
            Map<String, String> map = new HashMap<>();
            map.put("black", "#000000");
            return map;
        }
    }
}
