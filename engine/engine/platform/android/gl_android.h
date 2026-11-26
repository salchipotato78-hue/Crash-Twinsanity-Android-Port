// platform/android/gl_android.h -- PS2R_GL_HEADER on Android: OpenGL ES 3.1 plus shims for the desktop GL 4.x calls
// the render backend makes that ES 3.1 does not have. The shims keep the GL names, so the backend's call sites are
// unchanged. Like gl_vita.h, the texture sizes ES cannot query are recorded when a level-0 image is defined.
//
// What the shims do and what they give up:
//  - glNamedBuffer* (GL 4.5 direct state access): through the GL_COPY_WRITE_BUFFER binding, which is put back.
//  - glDrawBuffer, glDepthRange, glClearDepth: ES's glDrawBuffers, glDepthRangef, glClearDepthf.
//  - glGetTexLevelParameteriv: the size of the bound texture's level 0 (halved per level), as last defined.
//  - GL_SAMPLES_PASSED: ES's boolean GL_ANY_SAMPLES_PASSED (0 or 1 instead of a count; the profiler only).
//  - GL_TIME_ELAPSED queries (the GPU profiler): ES has no timer queries on the core profile, so they are accepted,
//    never run, and report 0 ns. The profiler shows zeros on Android; it does not change what is drawn.
#pragma once

#include <GLES3/gl31.h>

#include <cstdint>
#include <unordered_map>
#include <unordered_set>

// --- types and enums that ES 3.1 does not declare ----------------------------
#ifndef GL_DOUBLE_TYPE_NOT_IN_ES
#define GL_DOUBLE_TYPE_NOT_IN_ES 1
typedef double GLdouble;
typedef uint64_t GLuint64;
#endif
#ifndef GL_TIME_ELAPSED
#define GL_TIME_ELAPSED 0x88BF
#endif
// ES has only the boolean sample query: the profiler's sample counts read 0 or 1 on Android (a display figure only).
#ifndef GL_SAMPLES_PASSED
#define GL_SAMPLES_PASSED GL_ANY_SAMPLES_PASSED
#endif
#ifndef GL_TEXTURE_WIDTH
#define GL_TEXTURE_WIDTH 0x1000
#endif
#ifndef GL_TEXTURE_HEIGHT
#define GL_TEXTURE_HEIGHT 0x1001
#endif

namespace ps2r_android_gl {
struct TexSize {
    GLsizei width;
    GLsizei height;
};
// Per texture name: the size of its level 0 as last defined through glTexImage2D.
inline std::unordered_map<GLuint, TexSize>& TexSizes() {
    static std::unordered_map<GLuint, TexSize> sizes;
    return sizes;
}
// The timer queries accepted but never run (see the header comment).
inline std::unordered_set<GLuint>& TimeQueries() {
    static std::unordered_set<GLuint> queries;
    return queries;
}
}  // namespace ps2r_android_gl

// --- direct state access (GL 4.5) on ES 3.1 ----------------------------------
inline void glNamedBufferData(GLuint buffer, GLsizeiptr size, const void* data, GLenum usage) {
    GLint previous = 0;
    glGetIntegerv(GL_COPY_WRITE_BUFFER_BINDING, &previous);
    glBindBuffer(GL_COPY_WRITE_BUFFER, buffer);
    glBufferData(GL_COPY_WRITE_BUFFER, size, data, usage);
    glBindBuffer(GL_COPY_WRITE_BUFFER, GLuint(previous));
}

inline void glNamedBufferSubData(GLuint buffer, GLintptr offset, GLsizeiptr size, const void* data) {
    GLint previous = 0;
    glGetIntegerv(GL_COPY_WRITE_BUFFER_BINDING, &previous);
    glBindBuffer(GL_COPY_WRITE_BUFFER, buffer);
    glBufferSubData(GL_COPY_WRITE_BUFFER, offset, size, data);
    glBindBuffer(GL_COPY_WRITE_BUFFER, GLuint(previous));
}

// --- desktop names for the ES calls that differ ------------------------------
inline void glDrawBuffer(GLenum mode) { glDrawBuffers(1, &mode); }
inline void glDepthRange(GLdouble near_value, GLdouble far_value) {
    glDepthRangef(GLfloat(near_value), GLfloat(far_value));
}
inline void glClearDepth(GLdouble depth) { glClearDepthf(GLfloat(depth)); }

// --- texture sizes ES cannot query ---------------------------------------------
inline void glGetTexLevelParameteriv(GLenum target, GLint level, GLenum pname, GLint* params) {
    *params = 0;
    if (target != GL_TEXTURE_2D || level < 0) return;
    GLint bound = 0;
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &bound);
    const auto& sizes = ps2r_android_gl::TexSizes();
    const auto it = sizes.find(GLuint(bound));
    if (it == sizes.end()) return;
    const int width = it->second.width >> level > 0 ? it->second.width >> level : 1;
    const int height = it->second.height >> level > 0 ? it->second.height >> level : 1;
    if (pname == GL_TEXTURE_WIDTH) *params = width;
    if (pname == GL_TEXTURE_HEIGHT) *params = height;
}

// --- wrappers for ES names whose behaviour the backend depends on -----------------
// Defined before the macros below, so their bodies call the real ES functions.
inline void ps2r_android_TexImage2D(GLenum target, GLint level, GLint internal_format, GLsizei width, GLsizei height,
                                    GLint border, GLenum format, GLenum type, const void* data) {
    glTexImage2D(target, level, internal_format, width, height, border, format, type, data);
    if (target == GL_TEXTURE_2D && level == 0) {
        GLint bound = 0;
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &bound);
        ps2r_android_gl::TexSizes()[GLuint(bound)] = {width, height};
    }
}

inline void ps2r_android_DeleteTextures(GLsizei count, const GLuint* textures) {
    for (GLsizei i = 0; i < count; ++i) ps2r_android_gl::TexSizes().erase(textures[i]);
    glDeleteTextures(count, textures);
}

inline void ps2r_android_BeginQuery(GLenum target, GLuint id) {
    if (target == GL_TIME_ELAPSED) {
        ps2r_android_gl::TimeQueries().insert(id);
        return;
    }
    glBeginQuery(target, id);
}

inline void ps2r_android_EndQuery(GLenum target) {
    if (target == GL_TIME_ELAPSED) return;
    glEndQuery(target);
}

// ES 3.1 has only glGetQueryObjectuiv; GL's signed variant is the same query read as an int.
inline void glGetQueryObjectiv(GLuint id, GLenum pname, GLint* params) {
    if (ps2r_android_gl::TimeQueries().count(id) != 0) {
        *params = pname == GL_QUERY_RESULT_AVAILABLE ? 1 : 0;
        return;
    }
    GLuint value = 0;
    glGetQueryObjectuiv(id, pname, &value);
    *params = GLint(value);
}

// GL 4.x's 64-bit query result. Time queries report 0; the rest come from the 32-bit ES query.
inline void glGetQueryObjectui64v(GLuint id, GLenum pname, GLuint64* params) {
    if (ps2r_android_gl::TimeQueries().count(id) != 0) {
        *params = 0;
        return;
    }
    GLuint value = 0;
    glGetQueryObjectuiv(id, pname, &value);
    *params = value;
}

// The macros redirect every later call in the translation unit, so the backend uses the wrappers unchanged.
#define glTexImage2D ps2r_android_TexImage2D
#define glDeleteTextures ps2r_android_DeleteTextures
#define glBeginQuery ps2r_android_BeginQuery
#define glEndQuery ps2r_android_EndQuery
