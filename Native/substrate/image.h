/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
/**
 * Load and mmap the binary boot image of the VM.
 *
 * @author Bernd Mathiske
 */
#ifndef __image_h__
#define __image_h__ 1

#include "word.h"
#include "jni.h"

/*
 * Definition of fields in the image header struct.
 */
#define IMAGE_HEADER_FIELDS(f) \
    f(isBigEndian) /* 0: LITTLE, anything else: BIG. Must be first.  */ \
    f(identification) /* Magic number that must be present and have the same value in every Maxine boot image file  */ \
    f(version) /* Version of boot image file format  */ \
    f(randomID) /* Specific to one particular instance of boot image file  */ \
    f(wordSize) /* 4 or 8  */ \
    f(cacheAlignment) \
    f(pageSize) /* multiple of 1024  */ \
    f(vmRunMethodOffset) \
    f(vmThreadAddMethodOffset) \
    f(vmThreadRunMethodOffset) \
    f(vmThreadAttachMethodOffset) \
    f(vmThreadDetachMethodOffset) \
    f(classRegistryOffset) \
    f(stringDataSize) \
    f(relocationDataSize) \
    f(heapSize) /* multiple of 'pageSize'  */ \
    f(codeSize) /* multiple of 'pageSize'  */ \
    f(dynamicHeapRegionsArrayOffset) \
    f(auxiliarySpaceSize) /* Some extra space that the substrate allocates by malloc(). Used e.g. for the primordial card table.  */ \
    f(threadLocalsListHeadOffset) /* See the comment for the 'threadLocalsListHead' field in the VmThreadMap class.  */ \
    f(primordialThreadLocalsOffset) \
    f(threadLocalsAreaSize) /* The size of a thread locals area.  */ \
    f(SAFEPOINT_LATCH) \
    f(SAFEPOINTS_ENABLED_THREAD_LOCALS) \
    f(SAFEPOINTS_DISABLED_THREAD_LOCALS) \
    f(SAFEPOINTS_TRIGGERED_THREAD_LOCALS) \
    f(NATIVE_THREAD_LOCALS) \
    f(FORWARD_LINK) \
    f(BACKWARD_LINK) \
    f(ID) \
    f(JNI_ENV) \
    f(LAST_JAVA_FRAME_ANCHOR) \
    f(TRAP_NUMBER) \
    f(TRAP_INSTRUCTION_POINTER) \
    f(TRAP_FAULT_ADDRESS) \
    f(TRAP_LATCH_REGISTER) \
    f(STACK_REFERENCE_MAP) \
    f(STACK_REFERENCE_MAP_SIZE)

#define DEFINE_IMAGE_HEADER_FIELD(name) jint name;

/**
 *  ATTENTION: this struct and the below items must match
 *             'com.sun.max.vm.prototype.BootImage.Header'.
 */
typedef struct image_Header {
   IMAGE_HEADER_FIELDS(DEFINE_IMAGE_HEADER_FIELD)
} *image_Header;

/**
 * Must only be called after calling 'load_image()'.
 *
 * @return a pointer to information about the boot image as described in the struct above
 */
extern image_Header image_header(void);

/*
 *  ATTENTION: this must match 'com.sun.max.vm.prototype.BootImage.StringInfo'.
 */
typedef struct image_StringInfo {
    char *buildLevel;
    char *processorModel;
    char *instructionSet;
    char *operatingSystem;

    char *gripPackageName;
    char *referencePackageName;
    char *layoutPackageName;
    char *heapPackageName;
    char *monitorPackageName;
    char *compilerPackageName;
    char *jitPackageName;
    char *trampolinePackageName;
    char *targetABIsPackageName;
    char *runPackageName;
} *image_StringInfo;

/**
 * Must only be called after calling 'load_image()'.
 *
 * @return a pointer to information about the boot image as described in the struct above
 */
extern image_StringInfo image_stringInfo(void);

/**
 * ATTENTION: this struct must match 'com.sun.max.vm.prototype.BootImage.Trailer'.
 */
typedef struct image_Trailer {
    jint randomID;
    jint version;
    jint identification;
} *image_Trailer;

/**
 *  Read and verify the boot image file header, the string info section and the trailer,
 *  then verify these, then mmap the boot image, then relocate pointers in it.
 *
 *  Subsequently, after the string section:
 *   - relocation data
 *   - page padding
 *   - boot heap data
 *   - boot code data
 *
 * @param imageFileName full path of the boot image file
 */
extern int image_load(char *imageFileName);

/**
 * Must only be called after calling 'load_image()'.
 *
 * @return a pointer to the boot heap region
 */
extern Address image_heap(void);

/**
 * Must only be called after calling 'load_image()'.
 *
 * @return a pointer to the boot code region
 */
extern Address image_code(void);

/**
 * Must only be called after calling 'load_image()'.
 *
 * @return a pointer to the end of the boot code region
 */
extern Address image_code_end(void);


/**
 * Gets an address in the boot image based on a known offset in the image.
 *
 * Must only be called after calling 'load_image()'.
 *
 * @param type the type of the value
 * @param offset an offset in the image
 *
 * @return the effective address computed by 'image_heap() + offset', cast to 'type'
 */
#define image_offset_as_address(type, offset) ((type) (image_heap() + image_header()->offset))

/**
 * Reads a value from the boot image whose address is at a known offset from the start of the image.
 *
 * Must only be called after calling 'load_image()'.
 *
 * @param type the type of the value
 * @param offset the offset of the value. This denotes a member of the image_Header struct whose name end with "Offset".
 *
 * @return the value at 'image_heap() + offset', cast to 'type'
 */
#define image_read_value(type, offset) (*((type *) (image_heap() + image_header()->offset)))

/**
 * Writes a value in the boot image whose address is at a known offset from the start of the image.
 *
 * Must only be called after calling 'load_image()'.
 *
 * @param type the type of the value
 * @param offset the offset of the value. This denotes a member of the image_Header struct whose name end with "Offset".
 * @param value the value to write
 */
#define image_write_value(type, offset, value) do { \
    type *__fieldAddress = (type *) (image_heap() + image_header()->offset); \
    *__fieldAddress = value; \
} while(0)

#endif /*__image_h__*/
