#include <jni.h>
#include <time.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#include "fitz.h"
#include "mupdf.h"

#define LOG_TAG "libmupdf"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

fz_colorspace *colorspace;
fz_glyph_cache *glyphcache;
pdf_xref *xref;
int pagenum = 1;
int resolution = 160;
float pageWidth = 100;
float pageHeight = 100;
fz_display_list *currentPageList;
fz_rect currentMediabox;
int currentRotate;

JNIEXPORT int JNICALL Java_cam_pdftab_PdfCore_openFile(JNIEnv *env, jobject thiz, jstring jfilename)
{
	const char *filename;
	char *password = "";
	fz_error error;
	int pages;

	filename = (*env)->GetStringUTFChars(env, jfilename, NULL);
	if(filename == NULL)
	{
		LOGE("Failed to get filename");
		return 0;
	}

	fz_accelerate();
	glyphcache = fz_new_glyph_cache();
	colorspace = fz_device_bgr;

	error = pdf_open_xref(&xref, filename, password);
	if(error)
	{
		LOGE("Cannot open document: '%s'\n", filename);
		return 0;
	}

	error = pdf_load_page_tree(xref);
	if(error)
	{
		LOGE("Cannot load page tree: '%s'\n", filename);
		return 0;
	}

	pages = pdf_count_pages(xref);
	return pages;
}

JNIEXPORT void JNICALL Java_cam_pdftab_PdfCore_gotoPageInternal(JNIEnv *env, jobject thiz, int page)
{
	float zoom;
	fz_matrix ctm;
	fz_bbox bbox;
	fz_error error;
	fz_device *dev;
	pdf_page *currentPage;
	clock_t end, start = clock();

	//In the event of an error, ensure we give a non-empty page
	pageWidth = 100;
	pageHeight = 100;

	LOGE("Goto page %d...", page);
	if(currentPageList != NULL)
	{
		fz_free_display_list(currentPageList);
		currentPageList = NULL;
	}

	pagenum = page;
	error = pdf_load_page(&currentPage, xref, pagenum);
	if(error) return;

	zoom = resolution / 72;
	currentMediabox = currentPage->mediabox;
	currentRotate = currentPage->rotate;
	ctm = fz_translate(0, -currentMediabox.y1);
	ctm = fz_concat(ctm, fz_scale(zoom, -zoom));
	ctm = fz_concat(ctm, fz_rotate(currentRotate));
	bbox = fz_round_rect(fz_transform_rect(ctm, currentMediabox));
	pageWidth = bbox.x1 - bbox.x0;
	pageHeight = bbox.y1 - bbox.y0;

	//render
	currentPageList = fz_new_display_list();
	dev = fz_new_list_device(currentPageList);
	error = pdf_run_page(xref, currentPage, dev, fz_identity);
	pdf_free_page(currentPage);

	if(error)
		LOGE("cannot make displaylist from page %d", pagenum);

	fz_free_device(dev);
	end = clock();
	LOGE("Load = %10.7fms", (1000.0 * ((double)(end - start))) / CLOCKS_PER_SEC);
}

JNIEXPORT float JNICALL Java_cam_pdftab_PdfCore_getPageWidth(JNIEnv *env, jobject thiz)
{
	return pageWidth;
}

JNIEXPORT float JNICALL Java_cam_pdftab_PdfCore_getPageHeight(JNIEnv *env, jobject thiz)
{
	return pageHeight;
}

JNIEXPORT jint JNICALL Java_cam_pdftab_PdfCore_findLink(JNIEnv *env, jobject thiz,
	int pnum, int pageW, int pageH, int patchX, int patchY, int patchW, int patchH, int x, int y)
{
	float zoom;
	fz_matrix ctm;
	float xscale, yscale;
	fz_bbox bbox;
	pdf_link *link;
	fz_point p;
	fz_rect mediabox;
	pdf_page *page;
	int rotate, ret = -1;

	if(!pdf_load_page(&page, xref, pnum))
	{
		p.x = x;
		p.y = y;

		mediabox = page->mediabox;
		rotate = page->rotate;

		zoom = resolution / 72;
		ctm = fz_translate(-mediabox.x0, -mediabox.y1);
		ctm = fz_concat(ctm, fz_scale(zoom, -zoom));
		ctm = fz_concat(ctm, fz_rotate(rotate));
		bbox = fz_round_rect(fz_transform_rect(ctm,mediabox));

		//now, adjust ctm so that it would give the correct page width heights.
		xscale = (float)pageW / (float)(bbox.x1 - bbox.x0);
		yscale = (float)pageH / (float)(bbox.y1 - bbox.y0);
		ctm = fz_concat(ctm, fz_scale(xscale, yscale));
		bbox = fz_round_rect(fz_transform_rect(ctm, mediabox));
	
		ctm = fz_invert_matrix(ctm);
		p = fz_transform_point(ctm, p);
 
		for(link = page->links; link; link = link->next)
		{
			if(p.x >= link->rect.x0 && p.x <= link->rect.x1)
				if(p.y >= link->rect.y0 && p.y <= link->rect.y1)
					break;
		}

		if(link)
			ret = pdf_find_page_number(xref, fz_array_get(link->dest, 0));

		pdf_free_page(page);
	}

	return ret;
}

JNIEXPORT jboolean JNICALL Java_cam_pdftab_PdfCore_drawPage(JNIEnv *env, jobject thiz,
	jintArray buf, int pageW, int pageH, int patchX, int patchY, int patchW, int patchH)
{
	int ret;
	fz_error error;
	fz_device *dev;
	float zoom;
	fz_matrix ctm;
	fz_bbox bbox;
	fz_pixmap *pix;
	float xscale, yscale;
	fz_bbox rect;
	jint *pixels;
	clock_t end, start = clock();

	//call mupdf to render display list to screen
	LOGE("Rendering page=%dx%d patch=[%d,%d,%d,%d]", pageW, pageH, patchX, patchY, patchW, patchH);
	pixels = (*env)->GetPrimitiveArrayCritical(env, buf, 0);

	rect.x0 = patchX;
	rect.y0 = patchY;
	rect.x1 = patchX + patchW;
	rect.y1 = patchY + patchH;
	pix = fz_new_pixmap_with_rect_and_data(colorspace, rect, (unsigned char *)pixels);
	if(currentPageList == NULL)
	{
		fz_clear_pixmap_with_color(pix, 0xd0);
		return 0;
	}
	fz_clear_pixmap_with_color(pix, 0xff);

	zoom = resolution / 72;
	ctm = fz_translate(-currentMediabox.x0, -currentMediabox.y1);
	ctm = fz_concat(ctm, fz_scale(zoom, -zoom));
	ctm = fz_concat(ctm, fz_rotate(currentRotate));
	bbox = fz_round_rect(fz_transform_rect(ctm,currentMediabox));

	//now, adjust ctm so that it would give the correct page width heights.
	xscale = (float)pageW / (float)(bbox.x1 - bbox.x0);
	yscale = (float)pageH / (float)(bbox.y1 - bbox.y0);
	ctm = fz_concat(ctm, fz_scale(xscale, yscale));
	bbox = fz_round_rect(fz_transform_rect(ctm, currentMediabox));

	dev = fz_new_draw_device(glyphcache, pix);
	fz_execute_display_list(currentPageList, dev, ctm, bbox);
	fz_free_device(dev);
	fz_drop_pixmap(pix);

	(*env)->ReleasePrimitiveArrayCritical(env, buf, pixels, 0);
	end = clock();
	LOGE("Rendered = %10.7fms", (1000.0 * ((double)(end - start))) / CLOCKS_PER_SEC);
	return 1;
}

JNIEXPORT void JNICALL Java_cam_pdftab_PdfCore_destroying(JNIEnv *env, jobject thiz)
{
	fz_free_display_list(currentPageList);
	currentPageList = NULL;
	pdf_free_xref(xref);
	xref = NULL;
	fz_free_glyph_cache(glyphcache);
	glyphcache = NULL;
}
