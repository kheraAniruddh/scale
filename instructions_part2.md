
# Task:
1. use url = "https://litellm-proxy.ml.scale.com/v1/chat/completions"
   headers = {
   "Content-Type": "application/json",
   "Authorization": "Bearer sk-mBqGiIeQI72-Y2mR1SKS_Q"
   }
   data = {
   "model": "openai/gpt-4o",
   "messages": [
   {
   "role": "user",
   "content": "Hello!"
   }
   ]
   } 
to categorize users and tasks based on the parse json object by the parser. Add column category for task and specialization for user.
   Task Categories include: Legal, Automotive, Geography, Healthcare, Math, Music
   User Specializations include: Mechanic/Technician, Healthcare Professional, Legal Professional, Software Developer, Other, Student

# Clarifications & Assumptions:
1. Ensure the task category returned by llm is as mentioned in task allowed task categories
2. Ensure the user Specializations returned by llm is as mentioned in user allowed user Specializations
3. if any transient error retry up to max 3 times with exponential backing off - drop the row

# Design & Decisions:
1.  add new component package in extension to part1, as classifier, have llm interface and then concrete class for using the above llm http client. keep the configuration in application.properties for being configurable
2. keep the allowed task and user categories in  application.properties for being configurable
3. for LLM API_SECRET_KEY in .env, configure it in application.properties for being configurable
4. add max retry,timeout as well in application.properties for being configurable
5. the classifier once get response from the llm verfies the category/specialization as configured in properties file
# Implementation:


# Tests:
1. happy path
2. transient error retry
3. transient error drop the row


post /jobs -> job_id -for traking result