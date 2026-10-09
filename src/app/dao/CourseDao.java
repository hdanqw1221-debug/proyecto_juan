package app.dao;

import app.model.Course;
import app.model.StudentCourseAssignment;

import java.util.List;

public interface CourseDao {
    long create(Course course);
    List<Course> listAll();
    Course findById(long courseId);
    void update(Course course);
    void delete(long courseId);
    List<Course> listByTeacher(long teacherId);
    List<Course> listByStudent(long studentId);
    void assignStudentToCourse(long studentId, long courseId);
    void removeStudentFromCourse(long studentId, long courseId);
    List<StudentCourseAssignment> listStudentAssignments();
}
