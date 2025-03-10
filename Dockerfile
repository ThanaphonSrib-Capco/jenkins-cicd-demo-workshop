# Use the official Nginx image as the base
FROM nginx:alpine

# Copy your static HTML files to the Nginx HTML directory
COPY ./html /usr/share/nginx/html

# Expose port 80
EXPOSE 80

# Start Nginx when the container runs
CMD ["nginx", "-g", "daemon off;"]
